package com.hoanghnt.swiftpay.service;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hoanghnt.swiftpay.dto.response.TransactionResponse;
import com.hoanghnt.swiftpay.entity.Transaction;
import com.hoanghnt.swiftpay.entity.TransactionStatus;
import com.hoanghnt.swiftpay.entity.TransactionType;
import com.hoanghnt.swiftpay.entity.User;
import com.hoanghnt.swiftpay.exception.ErrorCode;
import com.hoanghnt.swiftpay.exception.custom.BusinessException;
import com.hoanghnt.swiftpay.exception.custom.ResourceNotFoundException;
import com.hoanghnt.swiftpay.repository.TransactionRepository;
import com.hoanghnt.swiftpay.repository.UserRepository;
import com.hoanghnt.swiftpay.repository.specification.TransactionSpecification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public Page<TransactionResponse> getTransactions(
            String username,
            TransactionType type,
            TransactionStatus status,
            LocalDateTime from,
            LocalDateTime to,
            Pageable pageable) {

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", username));

        Specification<Transaction> spec = Specification.allOf(
                TransactionSpecification.belongsToUser(user.getId()),
                TransactionSpecification.hasType(type),
                TransactionSpecification.hasStatus(status),
                TransactionSpecification.createdAfter(from),
                TransactionSpecification.createdBefore(to));

        return transactionRepository.findAll(spec, pageable)
                .map(this::toResponse);
    }

    private TransactionResponse toResponse(Transaction txn) {
        return new TransactionResponse(
                txn.getId(),
                txn.getType().name(),
                txn.getStatus().name(),
                txn.getAmount(),
                txn.getFee(),
                txn.getSender() != null ? txn.getSender().getUsername() : null,
                txn.getReceiver() != null ? txn.getReceiver().getUsername() : null,
                txn.getDescription(),
                txn.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public TransactionResponse getTransactionById(String username, UUID transactionId) {

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", username));

        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction",
                        transactionId.toString()));

        // Authorization: only sender or receiver may view this transaction
        boolean isSender = transaction.getSender() != null
                && transaction.getSender().getId().equals(user.getId());
        boolean isReceiver = transaction.getReceiver() != null
                && transaction.getReceiver().getId().equals(user.getId());

        if (!isSender && !isReceiver) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        return toResponse(transaction);
    }
}
