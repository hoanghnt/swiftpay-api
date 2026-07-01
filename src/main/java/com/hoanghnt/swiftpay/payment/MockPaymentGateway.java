package com.hoanghnt.swiftpay.payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Service;

import com.hoanghnt.swiftpay.entity.Transaction;
import com.hoanghnt.swiftpay.entity.TransactionStatus;
import com.hoanghnt.swiftpay.entity.TransactionType;
import com.hoanghnt.swiftpay.entity.User;
import com.hoanghnt.swiftpay.entity.Wallet;
import com.hoanghnt.swiftpay.exception.ErrorCode;
import com.hoanghnt.swiftpay.exception.custom.BusinessException;
import com.hoanghnt.swiftpay.repository.TransactionRepository;
import com.hoanghnt.swiftpay.repository.WalletRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class MockPaymentGateway implements PaymentGatewayPort {

    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;

    @Override
    @Transactional
    public TopupInitResult initiate(User user, BigDecimal amount) {
        walletRepository.findByUserId(user.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.WALLET_NOT_FOUND));

        String txnRef = generateTxnRef();
        Transaction transaction = Transaction.builder()
                .idempotencyKey(UUID.randomUUID())
                .sender(null)
                .receiver(user)
                .amount(amount)
                .type(TransactionType.TOPUP)
                .status(TransactionStatus.PENDING)
                .description("Mock top-up: " + txnRef)
                .vnpTxnRef(txnRef)
                .build();
        transactionRepository.save(transaction);

        String paymentUrl = "/mock-payment/pay?txnRef=" + txnRef;
        log.info("Mock payment initiated for user={}, txnRef={}", user.getUsername(), txnRef);

        return new TopupInitResult(transaction.getId(), txnRef, paymentUrl, amount, "PENDING");
    }

    @Override
    @Transactional
    public TopupConfirmResult confirm(String txnRef) {
        Transaction transaction = transactionRepository.findByVnpTxnRef(txnRef)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

        if (transaction.getStatus() != TransactionStatus.PENDING) {
            throw new BusinessException(ErrorCode.PAYMENT_ALREADY_PROCESSED);
        }

        Wallet wallet = walletRepository.findByUserId(transaction.getReceiver().getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.WALLET_NOT_FOUND));
        wallet.setBalance(wallet.getBalance().add(transaction.getAmount()));
        walletRepository.save(wallet);

        transaction.setStatus(TransactionStatus.COMPLETED);
        transactionRepository.save(transaction);

        log.info("Mock payment confirmed txnRef={}, amount={}", txnRef, transaction.getAmount());
        return new TopupConfirmResult(transaction.getId(), txnRef, transaction.getAmount(), "COMPLETED");
    }

    @Override
    @Transactional
    public TopupConfirmResult cancel(String txnRef) {
        Transaction transaction = transactionRepository.findByVnpTxnRef(txnRef)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

        if (transaction.getStatus() != TransactionStatus.PENDING) {
            throw new BusinessException(ErrorCode.PAYMENT_ALREADY_PROCESSED);
        }

        transaction.setStatus(TransactionStatus.FAILED);
        transaction.setFailureReason("Cancelled by user");
        transactionRepository.save(transaction);

        log.info("Mock payment cancelled txnRef={}", txnRef);
        return new TopupConfirmResult(transaction.getId(), txnRef, transaction.getAmount(), "FAILED");
    }

    private String generateTxnRef() {
        for (int i = 0; i < 5; i++) {
            String timestamp = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            String random = String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
            String txnRef = timestamp + random;

            if (!transactionRepository.existsByVnpTxnRef(txnRef)) {
                return txnRef;
            }
        }

        throw new BusinessException(ErrorCode.INTERNAL_ERROR);
    }
}
