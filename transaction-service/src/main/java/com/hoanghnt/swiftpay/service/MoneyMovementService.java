package com.hoanghnt.swiftpay.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import com.hoanghnt.swiftpay.audit.AuditEventType;
import com.hoanghnt.swiftpay.audit.AuditService;
import com.hoanghnt.swiftpay.client.WalletBusinessException;
import com.hoanghnt.swiftpay.client.WalletServiceClient;
import com.hoanghnt.swiftpay.client.WalletUnavailableException;
import com.hoanghnt.swiftpay.client.dto.InternalDebitRequest;
import com.hoanghnt.swiftpay.client.dto.InternalTransferRequest;
import com.hoanghnt.swiftpay.config.WalletProperties;
import com.hoanghnt.swiftpay.dto.request.TransferRequest;
import com.hoanghnt.swiftpay.dto.request.WithdrawRequest;
import com.hoanghnt.swiftpay.dto.response.TransactionResponse;
import com.hoanghnt.swiftpay.dto.response.WalletLimitsResponse;
import com.hoanghnt.swiftpay.dto.response.WithdrawResponse;
import com.hoanghnt.swiftpay.entity.Transaction;
import com.hoanghnt.swiftpay.entity.UserRef;
import com.hoanghnt.swiftpay.exception.ErrorCode;
import com.hoanghnt.swiftpay.exception.custom.BusinessException;
import com.hoanghnt.swiftpay.exception.custom.ResourceNotFoundException;
import com.hoanghnt.swiftpay.metrics.BusinessMetrics;
import com.hoanghnt.swiftpay.repository.UserRefRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class MoneyMovementService {

    private final RedisTemplate<String, String> redisTemplate;
    private final WalletProperties walletProperties;
    private final WalletServiceClient walletServiceClient;
    private final TransactionRecordService recordService;
    private final UserRefRepository userRefRepository;
    private final EmailService emailService;
    private final AuditService auditService;
    private final BusinessMetrics businessMetrics;

    private static final String IDEMPOTENCY_PREFIX = "idempotency:";
    private static final long IDEMPOTENCY_TTL_HOURS = 24;
    private static final String DAILY_TRANSFER_PREFIX = "daily-transfer:";

    private static final RedisScript<Long> RESERVE_DAILY_SCRIPT = RedisScript.of("""
            local newTotal = redis.call('INCRBY', KEYS[1], ARGV[1])
            redis.call('EXPIRE', KEYS[1], ARGV[2])
            if newTotal > tonumber(ARGV[3]) then
                redis.call('INCRBY', KEYS[1], -tonumber(ARGV[1]))
                return -1
            end
            return newTotal
            """, Long.class);

    public TransactionResponse transfer(UUID senderId, String senderUsername,
            String idempotencyKey, TransferRequest request) {
        // 1. Idempotency-edge (Redis)
        String redisKey = IDEMPOTENCY_PREFIX + idempotencyKey;
        String cachedTxnId = redisTemplate.opsForValue().get(redisKey);
        if (cachedTxnId != null) {
            return recordService.toResponseById(UUID.fromString(cachedTxnId));
        }

        // 2. Amount limits
        if (request.amount().compareTo(walletProperties.getMinTransferAmount()) < 0) {
            throw new BusinessException(ErrorCode.TRANSFER_AMOUNT_TOO_LOW);
        }
        if (request.amount().compareTo(walletProperties.getMaxTransferAmount()) > 0) {
            throw new BusinessException(ErrorCode.TRANSFER_AMOUNT_TOO_HIGH);
        }

        // 3. Self-transfer
        if (senderUsername.equals(request.receiverUsername())) {
            throw new BusinessException(ErrorCode.SELF_TRANSFER_NOT_ALLOWED);
        }

        // 4. Receiver — resolve via the user_ref read-model (does not read the users table)
        UserRef receiver = userRefRepository.findByUsername(request.receiverUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User", request.receiverUsername()));

        // 5. Daily limit — atomic RESERVE (P4). Released if any later step fails.
        long amountScaled = request.amount().movePointRight(4).longValueExact();
        reserveDailyOrThrow(senderId, amountScaled);

        // 6. Record PENDING (durable before calling wallet-service)
        Transaction txn;
        try {
            txn = recordService.createPendingTransfer(senderId, senderUsername,
                    receiver.getId(), receiver.getUsername(),
                    UUID.fromString(idempotencyKey), request.amount(), request.description());
        } catch (DataIntegrityViolationException e) {
            releaseDaily(senderId, amountScaled);
            return recordService.toResponseByIdempotencyKey(UUID.fromString(idempotencyKey));
        }

        // 7. Call wallet-service (atomic debit+credit, idempotent by op_key = txn.id)
        try {
            walletServiceClient.transfer(new InternalTransferRequest(
                    senderId, receiver.getId(), request.amount(), txn.getId().toString()));
        } catch (WalletBusinessException e) {
            releaseDaily(senderId, amountScaled);
            recordService.markFailed(txn.getId(), e.getErrorCode().getCode());
            businessMetrics.transferFailed();
            throw new BusinessException(e.getErrorCode(), e.getMessage());
        } catch (WalletUnavailableException e) {
            releaseDaily(senderId, amountScaled);
            log.warn("wallet-service unavailable during transfer txnId={} — left PENDING for reconciliation",
                    txn.getId(), e);
            throw new BusinessException(ErrorCode.WALLET_SERVICE_UNAVAILABLE);
        }

        // 8. Success → COMPLETED + idempotency + event (daily already reserved in step 5)
        recordService.markTransferCompleted(txn.getId());
        businessMetrics.transferSucceeded();
        redisTemplate.opsForValue().set(redisKey, txn.getId().toString(), Duration.ofHours(IDEMPOTENCY_TTL_HOURS));
        auditService.logTransfer(senderUsername, senderId,
                receiver.getUsername(), receiver.getId(), request.amount());

        log.info("Transfer completed: {} → {} | amount={} | txnId={}",
                senderUsername, request.receiverUsername(), request.amount(), txn.getId());
        return recordService.toResponseById(txn.getId());
    }

    public WithdrawResponse withdraw(UUID userId, String username, String idempotencyKey, WithdrawRequest request) {
        // 1. Compute fee (HALF_UP, scale 4) + net amount received
        BigDecimal fee = request.amount()
                .multiply(walletProperties.getWithdrawFeePercent())
                .setScale(4, RoundingMode.HALF_UP);
        BigDecimal netAmount = request.amount().subtract(fee);

        // 2. Idempotency-edge (Redis)
        String redisKey = IDEMPOTENCY_PREFIX + idempotencyKey;
        String cachedTxnId = redisTemplate.opsForValue().get(redisKey);
        if (cachedTxnId != null) {
            TransactionResponse existing = recordService.toResponseById(UUID.fromString(cachedTxnId));
            return toWithdrawResponse(existing, request, fee, netAmount);
        }

        // 3. Record PENDING (durable before calling wallet-service)
        Transaction txn;
        try {
            txn = recordService.createPendingWithdraw(userId, username, UUID.fromString(idempotencyKey),
                    request.amount(), fee,
                    "Withdraw to " + request.bankName() + " - " + request.bankAccountNumber());
        } catch (DataIntegrityViolationException e) {
            TransactionResponse existing = recordService.toResponseByIdempotencyKey(UUID.fromString(idempotencyKey));
            return toWithdrawResponse(existing, request, fee, netAmount);
        }

        // 4. Call wallet-service debit (idempotent by op_key = txn.id)
        try {
            walletServiceClient.debit(new InternalDebitRequest(
                    userId, request.amount(), txn.getId().toString()));
        } catch (WalletBusinessException e) {
            recordService.markFailed(txn.getId(), e.getErrorCode().getCode());
            throw new BusinessException(e.getErrorCode(), e.getMessage());
        } catch (WalletUnavailableException e) {
            log.warn("wallet-service unavailable during withdraw txnId={} — left PENDING for reconciliation",
                    txn.getId(), e);
            throw new BusinessException(ErrorCode.WALLET_SERVICE_UNAVAILABLE);
        }

        // 5. Success → COMPLETED + idempotency
        recordService.markCompletedNoEvent(txn.getId());
        redisTemplate.opsForValue().set(redisKey, txn.getId().toString(), Duration.ofHours(IDEMPOTENCY_TTL_HOURS));

        // 6. Send email + audit (side effect, does not affect the transaction outcome)
        String email = userRefRepository.findById(userId).map(UserRef::getEmail).orElse(null);
        emailService.sendWithdrawEmail(email, username,
                request.amount(), fee, netAmount, txn.getId());
        auditService.log(AuditEventType.WITHDRAW, username, userId,
                "SUCCESS", request.amount(), null, null);

        log.info("Withdraw completed: user={} | amount={} | fee={} | txnId={}",
                username, request.amount(), fee, txn.getId());

        TransactionResponse completed = recordService.toResponseById(txn.getId());
        return toWithdrawResponse(completed, request, fee, netAmount);
    }

    private WithdrawResponse toWithdrawResponse(TransactionResponse txn, WithdrawRequest request,
            BigDecimal fee, BigDecimal netAmount) {
        return new WithdrawResponse(
                txn.transactionId(),
                txn.amount(),
                fee,
                netAmount,
                txn.status(),
                request.bankAccountNumber(),
                request.bankName(),
                txn.createdAt());
    }

    public WalletLimitsResponse getWalletLimits(UUID userId) {
        BigDecimal dailyUsed = getDailyTransferUsed(userId);
        BigDecimal dailyRemaining = walletProperties.getMaxDailyTransfer().subtract(dailyUsed);
        if (dailyRemaining.signum() < 0) {
            dailyRemaining = BigDecimal.ZERO;
        }

        return new WalletLimitsResponse(
                walletProperties.getMinTransferAmount(),
                walletProperties.getMaxTransferAmount(),
                walletProperties.getMaxDailyTransfer(),
                dailyUsed,
                dailyRemaining);
    }

    private String dailyTransferKey(UUID userId) {
        return DAILY_TRANSFER_PREFIX + userId + ":" + LocalDate.now(ZoneOffset.UTC);
    }

    private BigDecimal getDailyTransferUsed(UUID userId) {
        String value = redisTemplate.opsForValue().get(dailyTransferKey(userId));
        return value != null ? new BigDecimal(new java.math.BigInteger(value), 4) : BigDecimal.ZERO;
    }

    private void reserveDailyOrThrow(UUID userId, long amountScaled) {
        long maxScaled = walletProperties.getMaxDailyTransfer().movePointRight(4).longValueExact();
        Long newTotal = redisTemplate.execute(RESERVE_DAILY_SCRIPT, List.of(dailyTransferKey(userId)),
                Long.toString(amountScaled), Long.toString(secondsUntilMidnightUtc()), Long.toString(maxScaled));
        if (newTotal != null && newTotal < 0) {
            throw new BusinessException(ErrorCode.DAILY_TRANSFER_LIMIT_EXCEEDED);
        }
    }

    private void releaseDaily(UUID userId, long amountScaled) {
        try {
            redisTemplate.opsForValue().decrement(dailyTransferKey(userId), amountScaled);
        } catch (Exception e) {
            log.warn("Failed to release daily reservation userId={} scaled={}", userId, amountScaled, e);
        }
    }

    private long secondsUntilMidnightUtc() {
        LocalDateTime nextMidnight = LocalDate.now(ZoneOffset.UTC).plusDays(1).atStartOfDay();
        return Duration.between(LocalDateTime.now(ZoneOffset.UTC), nextMidnight).getSeconds();
    }
}
