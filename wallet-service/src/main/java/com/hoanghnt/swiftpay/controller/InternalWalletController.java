package com.hoanghnt.swiftpay.controller;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hoanghnt.swiftpay.dto.request.InternalCreditRequest;
import com.hoanghnt.swiftpay.dto.request.InternalDebitRequest;
import com.hoanghnt.swiftpay.dto.request.InternalTransferRequest;
import com.hoanghnt.swiftpay.dto.response.BaseResponse;
import com.hoanghnt.swiftpay.dto.response.WalletOperationResponse;
import com.hoanghnt.swiftpay.service.WalletService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/internal/wallets")
@RequiredArgsConstructor
@Tag(name = "Internal Wallet", description = "Service-to-service money movement (idempotent per op_key)")
public class InternalWalletController {

    private final WalletService walletService;

    @Operation(summary = "Atomic transfer between two wallets (debit sender + credit receiver)")
    @PostMapping("/transfer")
    public ResponseEntity<BaseResponse<WalletOperationResponse>> transfer(
            @Valid @RequestBody InternalTransferRequest request) {
        return ResponseEntity.ok(BaseResponse.success(walletService.transfer(request)));
    }

    @Operation(summary = "Credit a wallet (top-up confirm)")
    @PostMapping("/credit")
    public ResponseEntity<BaseResponse<WalletOperationResponse>> credit(
            @Valid @RequestBody InternalCreditRequest request) {
        return ResponseEntity.ok(BaseResponse.success(walletService.credit(request)));
    }

    @Operation(summary = "Debit a wallet (withdraw)")
    @PostMapping("/debit")
    public ResponseEntity<BaseResponse<WalletOperationResponse>> debit(
            @Valid @RequestBody InternalDebitRequest request) {
        return ResponseEntity.ok(BaseResponse.success(walletService.debit(request)));
    }

    @Operation(summary = "Check whether an op_key has already been applied (reconciliation)")
    @GetMapping("/operations/{opKey}")
    public ResponseEntity<BaseResponse<Map<String, Object>>> isApplied(@PathVariable String opKey) {
        boolean applied = walletService.isOperationApplied(opKey);
        return ResponseEntity.ok(BaseResponse.success(Map.of("opKey", opKey, "applied", applied)));
    }

    @Operation(summary = "Wallet summary (admin, via monolith)")
    @GetMapping("/summary")
    public ResponseEntity<BaseResponse<com.hoanghnt.swiftpay.dto.response.WalletSummaryResponse>> summary() {
        return ResponseEntity.ok(BaseResponse.success(walletService.getWalletSummary()));
    }

    @Operation(summary = "Get a wallet view by userId (admin, via monolith)")
    @GetMapping("/{userId}")
    public ResponseEntity<BaseResponse<com.hoanghnt.swiftpay.dto.response.WalletResponse>> getWallet(
            @PathVariable java.util.UUID userId) {
        var view = walletService.getWalletView(userId)
                .orElseThrow(() -> new com.hoanghnt.swiftpay.exception.custom.ResourceNotFoundException("Wallet", userId));
        return ResponseEntity.ok(BaseResponse.success(view));
    }

    @Operation(summary = "Freeze a wallet (admin, via monolith)")
    @PostMapping("/{userId}/freeze")
    public ResponseEntity<BaseResponse<Void>> freeze(@PathVariable UUID userId) {
        walletService.freezeWallet(userId);
        return ResponseEntity.ok(BaseResponse.ok("Wallet frozen successfully"));
    }

    @Operation(summary = "Unfreeze a wallet (admin, via monolith)")
    @PostMapping("/{userId}/unfreeze")
    public ResponseEntity<BaseResponse<Void>> unfreeze(@PathVariable UUID userId) {
        walletService.unfreezeWallet(userId);
        return ResponseEntity.ok(BaseResponse.ok("Wallet unfrozen successfully"));
    }
}
