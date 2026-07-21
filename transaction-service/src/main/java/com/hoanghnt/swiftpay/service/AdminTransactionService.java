package com.hoanghnt.swiftpay.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hoanghnt.swiftpay.dto.response.TransactionResponse;
import com.hoanghnt.swiftpay.dto.response.TransactionSummaryResponse;
import com.hoanghnt.swiftpay.entity.Transaction;
import com.hoanghnt.swiftpay.entity.TransactionStatus;
import com.hoanghnt.swiftpay.entity.TransactionType;
import com.hoanghnt.swiftpay.exception.custom.ResourceNotFoundException;
import com.hoanghnt.swiftpay.repository.TransactionRepository;
import com.hoanghnt.swiftpay.repository.specification.TransactionSpecification;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminTransactionService {

    private final TransactionRepository transactionRepository;
    private final TransactionRecordService recordService;

    @Transactional(readOnly = true)
    public Page<TransactionResponse> listAll(TransactionType type, TransactionStatus status,
            LocalDateTime from, LocalDateTime to, Pageable pageable) {
        Specification<Transaction> spec = Specification.allOf(
                TransactionSpecification.hasType(type),
                TransactionSpecification.hasStatus(status),
                TransactionSpecification.createdAfter(from),
                TransactionSpecification.createdBefore(to));
        return transactionRepository.findAll(spec, pageable).map(recordService::toResponse);
    }

    @Transactional(readOnly = true)
    public TransactionResponse getById(UUID id) {
        Transaction txn = transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", id.toString()));
        return recordService.toResponse(txn);
    }

    @Transactional(readOnly = true)
    public TransactionSummaryResponse summary() {
        LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
        return new TransactionSummaryResponse(
                transactionRepository.countSince(startOfToday),
                transactionRepository.sumTransferAmountSince(startOfToday));
    }
}
