package com.hoanghnt.swiftpay.controller;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hoanghnt.swiftpay.dto.response.AdminSummaryResponse;
import com.hoanghnt.swiftpay.dto.response.AdminUserDetailResponse;
import com.hoanghnt.swiftpay.dto.response.AdminUserResponse;
import com.hoanghnt.swiftpay.dto.response.BaseResponse;
import com.hoanghnt.swiftpay.dto.response.PageResponse;
import com.hoanghnt.swiftpay.dto.response.TransactionResponse;
import com.hoanghnt.swiftpay.entity.TransactionStatus;
import com.hoanghnt.swiftpay.entity.TransactionType;
import com.hoanghnt.swiftpay.service.AdminService;
import com.hoanghnt.swiftpay.service.WalletService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin", description = "Admin-only user management, wallet oversight, transaction monitoring, and system summary")
public class AdminController {

    private final AdminService adminService;
    private final WalletService walletService;

    @Operation(summary = "List users", description = "Paginated list of all users, optional search by username/email")
    @ApiResponse(responseCode = "200", description = "Users retrieved")
    @GetMapping("/users")
    public ResponseEntity<BaseResponse<PageResponse<AdminUserResponse>>> listUsers(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<AdminUserResponse> result = adminService.listUsers(search, pageable);
        return ResponseEntity.ok(BaseResponse.success(PageResponse.from(result)));
    }

    @Operation(summary = "Get user detail", description = "User detail including wallet info")
    @ApiResponse(responseCode = "200", description = "User found")
    @GetMapping("/users/{userId}")
    public ResponseEntity<BaseResponse<AdminUserDetailResponse>> getUserDetail(@PathVariable UUID userId) {
        AdminUserDetailResponse response = adminService.getUserDetail(userId);
        return ResponseEntity.ok(BaseResponse.success(response));
    }

    @Operation(summary = "Enable user", description = "Sets enabled = true")
    @ApiResponse(responseCode = "200", description = "User enabled")
    @PatchMapping("/users/{userId}/enable")
    public ResponseEntity<BaseResponse<Void>> enableUser(@PathVariable UUID userId) {
        adminService.enableUser(userId);
        return ResponseEntity.ok(BaseResponse.ok("User enabled successfully"));
    }

    @Operation(summary = "Disable user", description = "Sets enabled = false")
    @ApiResponse(responseCode = "200", description = "User disabled")
    @PatchMapping("/users/{userId}/disable")
    public ResponseEntity<BaseResponse<Void>> disableUser(@PathVariable UUID userId) {
        adminService.disableUser(userId);
        return ResponseEntity.ok(BaseResponse.ok("User disabled successfully"));
    }

    @Operation(summary = "Unlock user", description = "Resets failedLoginAttempts and lockedUntil")
    @ApiResponse(responseCode = "200", description = "User unlocked")
    @PatchMapping("/users/{userId}/unlock")
    public ResponseEntity<BaseResponse<Void>> unlockUser(@PathVariable UUID userId) {
        adminService.unlockUser(userId);
        return ResponseEntity.ok(BaseResponse.ok("User unlocked successfully"));
    }

    @Operation(summary = "Get wallet detail", description = "Wallet detail for a given user")
    @ApiResponse(responseCode = "200", description = "Wallet found")
    @GetMapping("/wallets/{userId}")
    public ResponseEntity<BaseResponse<AdminUserDetailResponse>> getWallet(@PathVariable UUID userId) {
        AdminUserDetailResponse response = adminService.getUserDetail(userId);
        return ResponseEntity.ok(BaseResponse.success(response));
    }

    @Operation(summary = "Freeze wallet", description = "Prevents all outgoing transactions from the specified wallet")
    @ApiResponse(responseCode = "200", description = "Wallet frozen")
    @PostMapping("/wallets/{userId}/freeze")
    public ResponseEntity<BaseResponse<Void>> freezeWallet(@PathVariable UUID userId) {
        walletService.freezeWallet(userId);
        return ResponseEntity.ok(BaseResponse.ok("Wallet frozen successfully"));
    }

    @Operation(summary = "Unfreeze wallet", description = "Restores transaction capability for the specified wallet")
    @ApiResponse(responseCode = "200", description = "Wallet unfrozen")
    @PostMapping("/wallets/{userId}/unfreeze")
    public ResponseEntity<BaseResponse<Void>> unfreezeWallet(@PathVariable UUID userId) {
        walletService.unfreezeWallet(userId);
        return ResponseEntity.ok(BaseResponse.ok("Wallet unfrozen successfully"));
    }

    @Operation(summary = "List all transactions", description = "All transactions in the system, not filtered by user")
    @ApiResponse(responseCode = "200", description = "Transactions retrieved")
    @GetMapping("/transactions")
    public ResponseEntity<BaseResponse<PageResponse<TransactionResponse>>> listAllTransactions(
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) TransactionStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<TransactionResponse> result = adminService.listAllTransactions(type, status, from, to, pageable);
        return ResponseEntity.ok(BaseResponse.success(PageResponse.from(result)));
    }

    @Operation(summary = "Get any transaction by ID", description = "Not restricted to sender/receiver ownership")
    @ApiResponse(responseCode = "200", description = "Transaction found")
    @GetMapping("/transactions/{txnId}")
    public ResponseEntity<BaseResponse<TransactionResponse>> getTransactionById(@PathVariable UUID txnId) {
        TransactionResponse response = adminService.getTransactionById(txnId);
        return ResponseEntity.ok(BaseResponse.success(response));
    }

    @Operation(summary = "System summary", description = "Quick stats snapshot: users, wallets, balances, today's transactions")
    @ApiResponse(responseCode = "200", description = "Summary retrieved")
    @GetMapping("/summary")
    public ResponseEntity<BaseResponse<AdminSummaryResponse>> getSummary() {
        AdminSummaryResponse response = adminService.getSummary();
        return ResponseEntity.ok(BaseResponse.success(response));
    }
}
