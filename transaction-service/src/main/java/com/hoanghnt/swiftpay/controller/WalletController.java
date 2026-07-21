package com.hoanghnt.swiftpay.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hoanghnt.swiftpay.dto.request.TopupRequest;
import com.hoanghnt.swiftpay.dto.request.WithdrawRequest;
import com.hoanghnt.swiftpay.dto.response.BaseResponse;
import com.hoanghnt.swiftpay.dto.response.TopupInitResult;
import com.hoanghnt.swiftpay.dto.response.WalletLimitsResponse;
import com.hoanghnt.swiftpay.dto.response.WithdrawResponse;
import com.hoanghnt.swiftpay.security.AuthPrincipal;
import com.hoanghnt.swiftpay.service.MoneyMovementService;
import com.hoanghnt.swiftpay.service.PaymentService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/wallet")
@RequiredArgsConstructor
@Tag(name = "Wallet Operations", description = "Top-up, withdraw, transfer limits")
public class WalletController {

    private final PaymentService paymentService;
    private final MoneyMovementService moneyMovementService;

    @Operation(summary = "Create mock top-up URL",
               description = "Generates a mock payment URL. Balance is credited after the mock payment is confirmed.")
    @PostMapping("/topup")
    public ResponseEntity<BaseResponse<TopupInitResult>> topup(
            Authentication authentication,
            @Valid @RequestBody TopupRequest request) {

        TopupInitResult response = paymentService.initiate(
                AuthPrincipal.userId(authentication), authentication.getName(), request.amount());
        return ResponseEntity.ok(BaseResponse.success("Payment URL created", response));
    }

    @Operation(summary = "Get transfer limits",
               description = "Returns min/max per-transaction transfer amount, daily cap, and today's usage.")
    @GetMapping("/limits")
    public ResponseEntity<BaseResponse<WalletLimitsResponse>> getLimits(Authentication authentication) {
        WalletLimitsResponse response = moneyMovementService.getWalletLimits(AuthPrincipal.userId(authentication));
        return ResponseEntity.ok(BaseResponse.success(response));
    }

    @Operation(summary = "Withdraw funds (mock)",
               description = "Deducts the amount from the wallet balance via wallet-service and records a WITHDRAW transaction. Requires a unique X-Idempotency-Key header.")
    @PostMapping("/withdraw")
    public ResponseEntity<BaseResponse<WithdrawResponse>> withdraw(
            Authentication authentication,
            @RequestHeader("X-Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody WithdrawRequest request) {

        WithdrawResponse response = moneyMovementService.withdraw(
                AuthPrincipal.userId(authentication), authentication.getName(), idempotencyKey, request);
        return ResponseEntity.ok(BaseResponse.success("Withdraw successful", response));
    }
}
