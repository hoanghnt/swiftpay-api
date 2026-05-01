package com.hoanghnt.swiftpay.service;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Service;

import com.hoanghnt.swiftpay.config.VNPayConfig;
import com.hoanghnt.swiftpay.dto.request.TopupRequest;
import com.hoanghnt.swiftpay.dto.response.TopupResponse;
import com.hoanghnt.swiftpay.entity.Transaction;
import com.hoanghnt.swiftpay.entity.TransactionStatus;
import com.hoanghnt.swiftpay.entity.TransactionType;
import com.hoanghnt.swiftpay.entity.User;
import com.hoanghnt.swiftpay.entity.Wallet;
import com.hoanghnt.swiftpay.exception.ErrorCode;
import com.hoanghnt.swiftpay.exception.custom.BusinessException;
import com.hoanghnt.swiftpay.exception.custom.ResourceNotFoundException;
import com.hoanghnt.swiftpay.repository.TransactionRepository;
import com.hoanghnt.swiftpay.repository.UserRepository;
import com.hoanghnt.swiftpay.repository.WalletRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class VNPayService {

    private final VNPayConfig vnPayConfig;
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;

    public TopupResponse createPaymentUrl(String username, TopupRequest request,
            String ipAddress) {
        // 1. Validate wallet exists
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", username));
        walletRepository.findByUserId(user.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.WALLET_NOT_FOUND));

        // 2. Create transaction record in PENDING status
        String txnRef = generateTxnRef(); // unique, 8 chars
        Transaction transaction = Transaction.builder()
                .idempotencyKey(UUID.randomUUID())
                .sender(null) // topup has no sender
                .receiver(user)
                .amount(request.amount())
                .type(TransactionType.TOPUP)
                .status(TransactionStatus.PENDING)
                .description("VNPay Top-up: " + txnRef)
                .vnpTxnRef(txnRef)
                .build();
        transactionRepository.save(transaction);

        // 3. Build VNPay params (important: sort alphabetically)
        Map<String, String> vnpParams = new TreeMap<>(); // TreeMap keeps keys sorted
        vnpParams.put("vnp_Version", vnPayConfig.getVersion());
        vnpParams.put("vnp_Command", vnPayConfig.getCommand());
        vnpParams.put("vnp_TmnCode", vnPayConfig.getTmnCode());
        vnpParams.put("vnp_Amount", String.valueOf(request.amount()
                .multiply(BigDecimal.valueOf(100)).longValue())); // VNPay amount is × 100
        vnpParams.put("vnp_CurrCode", vnPayConfig.getCurrCode());
        vnpParams.put("vnp_TxnRef", txnRef);
        vnpParams.put("vnp_OrderInfo", "Nap tien " + txnRef);
        vnpParams.put("vnp_OrderType", vnPayConfig.getOrderType());
        vnpParams.put("vnp_Locale", vnPayConfig.getLocale());
        vnpParams.put("vnp_ReturnUrl", vnPayConfig.getReturnUrl());
        vnpParams.put("vnp_IpAddr", ipAddress);
        vnpParams.put("vnp_CreateDate", getCurrentDateTime()); // yyyyMMddHHmmss

        // 4. Compute HMAC-SHA512
        String queryString = buildQueryString(vnpParams); // key=value&...
        String secureHash = hmacSha512(vnPayConfig.getHashSecret(), queryString);

        String paymentUrl = vnPayConfig.getPayUrl() + "?" + queryString
                + "&vnp_SecureHash=" + secureHash;

        log.info("VNPay payment URL created for user={}, txnRef={}", username, txnRef);

        return new TopupResponse(transaction.getId(), txnRef, paymentUrl,
                request.amount(), "PENDING");
    }

    private String generateTxnRef() {
        // Format: yyyyMMdd + 4 random digits
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String random = String.format("%04d", new Random().nextInt(10000));
        return date + random;
    }

    private String getCurrentDateTime() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
    }

    private String buildQueryString(Map<String, String> params) {
        return params.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)
                        + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    private String hmacSha512(String key, String data) {
        try {
            Mac hmac = Mac.getInstance("HmacSHA512");
            SecretKeySpec secretKey = new SecretKeySpec(
                    key.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
            hmac.init(secretKey);
            byte[] hash = hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash); // Java 17+ HexFormat
        } catch (Exception e) {
            throw new RuntimeException("HMAC-SHA512 error", e);
        }
    }

    @Transactional
    public Map<String, String> processIpn(Map<String, String> params) {
        // 1. Extract and remove secureHash from params
        String secureHash = params.get("vnp_SecureHash");
        Map<String, String> signParams = new TreeMap<>(params);
        signParams.remove("vnp_SecureHash");
        signParams.remove("vnp_SecureHashType");

        // 2. Verify signature
        String queryString = buildQueryString(signParams);
        String calculatedHash = hmacSha512(vnPayConfig.getHashSecret(), queryString);

        if (!calculatedHash.equalsIgnoreCase(secureHash)) {
            log.warn("VNPay IPN: invalid signature");
            return Map.of("RspCode", "97", "Message", "Invalid signature");
        }

        // 3. Find transaction by VNPay reference (vnp_TxnRef)
        String txnRef = params.get("vnp_TxnRef");
        String responseCode = params.get("vnp_ResponseCode");
        String vnpAmount = params.get("vnp_Amount"); // already in × 100 units
    
        Transaction transaction = transactionRepository.findByVnpTxnRef(txnRef)
                .orElse(null);

        if (transaction == null) {
            return Map.of("RspCode", "01", "Message", "Order not found");
        }

        // 4. Do not process twice (idempotency)
        if (transaction.getStatus() != TransactionStatus.PENDING) {
            return Map.of("RspCode", "02", "Message", "Order already confirmed");
        }

        // 5. Verify amount
        BigDecimal expectedAmount = transaction.getAmount()
                .multiply(BigDecimal.valueOf(100));
        if (!(expectedAmount.longValue() == Long.parseLong(vnpAmount))) {
            return Map.of("RspCode", "04", "Message", "Invalid amount");
        }

        // 6. Process result
        if ("00".equals(responseCode)) {
            // Payment success → credit wallet
            Wallet wallet = walletRepository.findByUserId(transaction.getReceiver().getId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.WALLET_NOT_FOUND));
            wallet.setBalance(wallet.getBalance().add(transaction.getAmount()));
            walletRepository.save(wallet);

            transaction.setStatus(TransactionStatus.COMPLETED);
            log.info("VNPay IPN: topup success txnRef={}, amount={}",
                    txnRef, transaction.getAmount());
        } else {
            transaction.setStatus(TransactionStatus.FAILED);
            transaction.setFailureReason("VNPay response code: " + responseCode);
            log.warn("VNPay IPN: topup failed txnRef={}, code={}", txnRef, responseCode);
        }

        transactionRepository.save(transaction);
        return Map.of("RspCode", "00", "Message", "Confirm success");
    }
}
