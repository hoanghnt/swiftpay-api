package com.hoanghnt.swiftpay.controller;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hoanghnt.swiftpay.dto.response.BaseResponse;
import com.hoanghnt.swiftpay.dto.response.PageResponse;
import com.hoanghnt.swiftpay.dto.response.TransactionResponse;
import com.hoanghnt.swiftpay.dto.response.TransactionSummaryResponse;
import com.hoanghnt.swiftpay.entity.TransactionStatus;
import com.hoanghnt.swiftpay.entity.TransactionType;
import com.hoanghnt.swiftpay.service.AdminTransactionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/internal/transactions")
@RequiredArgsConstructor
@Tag(name = "Internal Transactions", description = "Service-to-service admin read of all transactions")
public class InternalTransactionController {

    private final AdminTransactionService adminTransactionService;

    @Operation(summary = "List all transactions (admin, via monolith)")
    @GetMapping
    public ResponseEntity<BaseResponse<PageResponse<TransactionResponse>>> list(
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) TransactionStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<TransactionResponse> result = adminTransactionService.listAll(type, status, from, to, pageable);
        return ResponseEntity.ok(BaseResponse.success(PageResponse.from(result)));
    }

    @Operation(summary = "Transaction summary today (admin, via monolith)")
    @GetMapping("/summary")
    public ResponseEntity<BaseResponse<TransactionSummaryResponse>> summary() {
        return ResponseEntity.ok(BaseResponse.success(adminTransactionService.summary()));
    }

    @Operation(summary = "Get any transaction by id (admin, via monolith)")
    @GetMapping("/{id}")
    public ResponseEntity<BaseResponse<TransactionResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(BaseResponse.success(adminTransactionService.getById(id)));
    }
}
