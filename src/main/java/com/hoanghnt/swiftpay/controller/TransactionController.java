package com.hoanghnt.swiftpay.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hoanghnt.swiftpay.dto.request.TransferRequest;
import com.hoanghnt.swiftpay.dto.response.BaseResponse;
import com.hoanghnt.swiftpay.dto.response.PageResponse;
import com.hoanghnt.swiftpay.dto.response.TransactionResponse;
import com.hoanghnt.swiftpay.entity.TransactionStatus;
import com.hoanghnt.swiftpay.entity.TransactionType;
import com.hoanghnt.swiftpay.exception.ErrorCode;
import com.hoanghnt.swiftpay.exception.custom.BusinessException;
import com.hoanghnt.swiftpay.service.TransactionService;
import com.hoanghnt.swiftpay.service.WalletService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/transactions")
@RequiredArgsConstructor
@Tag(name = "Transactions", description = "P2P transfers and transaction history")
public class TransactionController {

    private final WalletService walletService;
    private final TransactionService transactionService;

    private static final List<String> ALLOWED_SORT_FIELDS = List.of("createdAt", "amount", "type", "status");

    @Operation(summary = "Transfer funds (P2P)",
               description = "Transfers funds from the authenticated user to another user. Requires a unique X-Idempotency-Key header to prevent duplicate transactions.")
    @ApiResponse(responseCode = "201", description = "Transfer completed")
    @PostMapping("/transfer")
    public ResponseEntity<BaseResponse<TransactionResponse>> transfer(
            Authentication authentication,
            @RequestHeader("X-Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody TransferRequest request) {

        // Validate idempotency key format
        try {
            UUID.fromString(idempotencyKey);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }

        TransactionResponse response = walletService.transfer(
                authentication.getName(),
                idempotencyKey,
                request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(BaseResponse.success("Transfer successful", response));
    }

    @Operation(summary = "List transactions",
               description = "Returns a paginated list of the authenticated user's transactions (as sender or receiver). " +
                             "Sortable fields: createdAt, amount, type, status. Max page size: 50.")
    @ApiResponse(responseCode = "200", description = "Transactions retrieved")
    @GetMapping
    public ResponseEntity<BaseResponse<PageResponse<TransactionResponse>>> getTransactions(
            Authentication authentication,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) TransactionStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        size = Math.min(size, 50);
        if (!ALLOWED_SORT_FIELDS.contains(sortBy)) {
            sortBy = "createdAt";
        }
        Sort sort = sortDir.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<TransactionResponse> result = transactionService.getTransactions(
                authentication.getName(), type, status, from, to, pageable);
        return ResponseEntity.ok(
                BaseResponse.success("Transactions fetched", PageResponse.from(result)));
    }

    @Operation(summary = "Get transaction by ID",
               description = "Returns details of a single transaction. Only accessible by the sender or receiver.")
    @ApiResponse(responseCode = "200", description = "Transaction found")
    @GetMapping("/{id}")
    public ResponseEntity<BaseResponse<TransactionResponse>> getTransactionById(
            Authentication authentication,
            @PathVariable String id) {

        UUID transactionId;
        try {
            transactionId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }

        TransactionResponse response = transactionService.getTransactionById(
                authentication.getName(), transactionId);

        return ResponseEntity.ok(BaseResponse.success("Transaction found", response));
    }
}