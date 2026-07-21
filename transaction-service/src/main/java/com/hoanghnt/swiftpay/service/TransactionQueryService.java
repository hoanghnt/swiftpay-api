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
import com.hoanghnt.swiftpay.exception.ErrorCode;
import com.hoanghnt.swiftpay.exception.custom.BusinessException;
import com.hoanghnt.swiftpay.exception.custom.ResourceNotFoundException;
import com.hoanghnt.swiftpay.repository.TransactionRepository;
import com.hoanghnt.swiftpay.repository.specification.TransactionSpecification;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TransactionQueryService {

    private final TransactionRepository transactionRepository;
    private final TransactionRecordService recordService;

    @Transactional(readOnly = true)
    public Page<TransactionResponse> getTransactions(
            UUID userId,
            TransactionType type,
            TransactionStatus status,
            LocalDateTime from,
            LocalDateTime to,
            Pageable pageable) {

        Specification<Transaction> spec = Specification.allOf(
                TransactionSpecification.belongsToUser(userId),
                TransactionSpecification.hasType(type),
                TransactionSpecification.hasStatus(status),
                TransactionSpecification.createdAfter(from),
                TransactionSpecification.createdBefore(to));

        return transactionRepository.findAll(spec, pageable).map(recordService::toResponse);
    }

    @Transactional(readOnly = true)
    public TransactionResponse getTransactionById(UUID userId, UUID transactionId) {

        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", transactionId.toString()));

        boolean isSender = userId.equals(transaction.getSenderId());
        boolean isReceiver = userId.equals(transaction.getReceiverId());

        if (!isSender && !isReceiver) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        return recordService.toResponse(transaction);
    }
}
