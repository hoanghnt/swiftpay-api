package com.hoanghnt.swiftpay.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hoanghnt.swiftpay.dto.response.TransactionResponse;
import com.hoanghnt.swiftpay.entity.Transaction;
import com.hoanghnt.swiftpay.entity.TransactionStatus;
import com.hoanghnt.swiftpay.entity.TransactionType;
import com.hoanghnt.swiftpay.event.PaymentSucceededDomainEvent;
import com.hoanghnt.swiftpay.event.TransactionCompletedDomainEvent;
import com.hoanghnt.swiftpay.exception.ErrorCode;
import com.hoanghnt.swiftpay.exception.custom.BusinessException;
import com.hoanghnt.swiftpay.exception.custom.ResourceNotFoundException;
import com.hoanghnt.swiftpay.repository.TransactionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionRecordService {

    private final TransactionRepository transactionRepository;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Transactional
    public Transaction createPendingTransfer(UUID senderId, String senderUsername,
            UUID receiverId, String receiverUsername, UUID idempotencyKey,
            BigDecimal amount, String description) {
        Transaction txn = Transaction.builder()
                .idempotencyKey(idempotencyKey)
                .senderId(senderId)
                .senderUsername(senderUsername)
                .receiverId(receiverId)
                .receiverUsername(receiverUsername)
                .amount(amount)
                .type(TransactionType.TRANSFER)
                .status(TransactionStatus.PENDING)
                .description(description)
                .build();
        return transactionRepository.save(txn);
    }

    @Transactional
    public Transaction createPendingWithdraw(UUID userId, String username, UUID idempotencyKey,
            BigDecimal amount, BigDecimal fee, String description) {
        Transaction txn = Transaction.builder()
                .idempotencyKey(idempotencyKey)
                .senderId(userId)
                .senderUsername(username)
                .amount(amount)
                .fee(fee)
                .type(TransactionType.WITHDRAW)
                .status(TransactionStatus.PENDING)
                .description(description)
                .build();
        return transactionRepository.save(txn);
    }

    @Transactional
    public Transaction createPendingTopup(UUID userId, String username, BigDecimal amount, String txnRef) {
        Transaction txn = Transaction.builder()
                .idempotencyKey(UUID.randomUUID())
                .receiverId(userId)
                .receiverUsername(username)
                .amount(amount)
                .type(TransactionType.TOPUP)
                .status(TransactionStatus.PENDING)
                .description("Mock top-up: " + txnRef)
                .vnpTxnRef(txnRef)
                .build();
        return transactionRepository.save(txn);
    }

    @Transactional
    public void markTransferCompleted(UUID txnId) {
        Transaction txn = load(txnId);
        if (txn.getStatus() == TransactionStatus.COMPLETED) {
            return;
        }
        txn.setStatus(TransactionStatus.COMPLETED);
        transactionRepository.save(txn);
        applicationEventPublisher.publishEvent(new TransactionCompletedDomainEvent(txn));
    }

    @Transactional
    public void markTopupCompleted(UUID txnId) {
        Transaction txn = load(txnId);
        if (txn.getStatus() == TransactionStatus.COMPLETED) {
            return;
        }
        txn.setStatus(TransactionStatus.COMPLETED);
        transactionRepository.save(txn);
        applicationEventPublisher.publishEvent(new PaymentSucceededDomainEvent(txn));
    }

    @Transactional
    public void markCompletedNoEvent(UUID txnId) {
        Transaction txn = load(txnId);
        if (txn.getStatus() == TransactionStatus.COMPLETED) {
            return;
        }
        txn.setStatus(TransactionStatus.COMPLETED);
        transactionRepository.save(txn);
    }

    @Transactional
    public void markFailed(UUID txnId, String failureReason) {
        Transaction txn = load(txnId);
        if (txn.getStatus() != TransactionStatus.PENDING) {
            return;
        }
        txn.setStatus(TransactionStatus.FAILED);
        txn.setFailureReason(failureReason);
        transactionRepository.save(txn);
    }

    public record PendingTopup(UUID txnId, UUID receiverUserId, BigDecimal amount) {}

    @Transactional(readOnly = true)
    public PendingTopup findPendingTopup(String txnRef) {
        Transaction txn = transactionRepository.findByVnpTxnRef(txnRef)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
        if (txn.getStatus() != TransactionStatus.PENDING) {
            throw new BusinessException(ErrorCode.PAYMENT_ALREADY_PROCESSED);
        }
        return new PendingTopup(txn.getId(), txn.getReceiverId(), txn.getAmount());
    }

    @Transactional(readOnly = true)
    public TransactionResponse toResponseById(UUID txnId) {
        return toResponse(load(txnId));
    }

    @Transactional(readOnly = true)
    public TransactionResponse toResponseByIdempotencyKey(UUID idempotencyKey) {
        Transaction txn = transactionRepository.findByIdempotencyKey(idempotencyKey)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));
        return toResponse(txn);
    }

    private Transaction load(UUID txnId) {
        return transactionRepository.findById(txnId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", txnId));
    }

    public TransactionResponse toResponse(Transaction txn) {
        return new TransactionResponse(
                txn.getId(),
                txn.getType().name(),
                txn.getStatus().name(),
                txn.getAmount(),
                txn.getFee(),
                txn.getSenderUsername(),
                txn.getReceiverUsername(),
                txn.getDescription(),
                txn.getCreatedAt());
    }

    public String generateTxnRef() {
        for (int i = 0; i < 5; i++) {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            String random = String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
            String txnRef = timestamp + random;
            if (!transactionRepository.existsByVnpTxnRef(txnRef)) {
                return txnRef;
            }
        }
        throw new BusinessException(ErrorCode.INTERNAL_ERROR);
    }
}
