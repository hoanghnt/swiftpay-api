package com.hoanghnt.swiftpay.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hoanghnt.swiftpay.dto.request.TopupRequest;
import com.hoanghnt.swiftpay.dto.request.WithdrawRequest;
import com.hoanghnt.swiftpay.dto.response.BaseResponse;
import com.hoanghnt.swiftpay.dto.response.TopupResponse;
import com.hoanghnt.swiftpay.dto.response.WalletResponse;
import com.hoanghnt.swiftpay.dto.response.WithdrawResponse;
import com.hoanghnt.swiftpay.service.VNPayService;
import com.hoanghnt.swiftpay.service.WalletService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/wallet")
@RequiredArgsConstructor
@Tag(name = "Wallet", description = "View balance, top-up via VNPay, withdraw, freeze/unfreeze (Admin)")
public class WalletController {

    private final WalletService walletService;
    private final VNPayService vnPayService;

    @Operation(summary = "Get my wallet",
               description = "Returns the current balance and status of the authenticated user's wallet")
    @ApiResponse(responseCode = "200", description = "Wallet retrieved successfully")
    @GetMapping("/me")
    public ResponseEntity<BaseResponse<WalletResponse>> getMyWallet(Authentication authentication) {
        WalletResponse response = walletService.getMyWallet(authentication.getName());
        return ResponseEntity.ok(BaseResponse.success(response));
    }

    @Operation(summary = "Create VNPay top-up URL",
               description = "Generates a VNPay payment URL. Redirect the user to this URL to complete payment. Balance is credited after IPN callback succeeds.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Payment URL created"),
            @ApiResponse(responseCode = "404", description = "Wallet not found")
    })
    @PostMapping("/topup")
    public ResponseEntity<BaseResponse<TopupResponse>> topup(
            Authentication authentication,
            @Valid @RequestBody TopupRequest request,
            HttpServletRequest httpRequest) {

        String ipAddress = httpRequest.getHeader("X-Forwarded-For");
        if (ipAddress == null) {
            ipAddress = httpRequest.getRemoteAddr();
        }

        TopupResponse response = vnPayService.createPaymentUrl(
                authentication.getName(), request, ipAddress);

        return ResponseEntity.ok(BaseResponse.success("Payment URL created", response));
    }

    @Operation(summary = "Withdraw funds (mock)",
               description = "Deducts the amount from the wallet balance and creates a WITHDRAW transaction. Does not call a real bank API.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Withdrawal successful"),
            @ApiResponse(responseCode = "400", description = "Insufficient balance"),
            @ApiResponse(responseCode = "403", description = "Wallet is frozen")
    })
    @PostMapping("/withdraw")
    public ResponseEntity<BaseResponse<WithdrawResponse>> withdraw(
            Authentication authentication,
            @Valid @RequestBody WithdrawRequest request) {

        WithdrawResponse response = walletService.withdraw(authentication.getName(), request);
        return ResponseEntity.ok(BaseResponse.success("Withdraw successful", response));
    }

    @Operation(summary = "Freeze wallet (Admin only)",
               description = "Prevents all outgoing transactions from the specified wallet")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Wallet frozen"),
            @ApiResponse(responseCode = "403", description = "Access denied — Admin role required"),
            @ApiResponse(responseCode = "409", description = "Wallet is already frozen")
    })
    @PostMapping("/{userId}/freeze")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BaseResponse<Void>> freeze(
            @PathVariable UUID userId) {

        walletService.freezeWallet(userId);
        return ResponseEntity.ok(BaseResponse.ok("Wallet frozen successfully"));
    }

    @Operation(summary = "Unfreeze wallet (Admin only)",
               description = "Restores transaction capability for the specified wallet")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Wallet unfrozen"),
            @ApiResponse(responseCode = "403", description = "Access denied — Admin role required"),
            @ApiResponse(responseCode = "409", description = "Wallet is not frozen")
    })
    @PostMapping("/{userId}/unfreeze")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BaseResponse<Void>> unfreeze(
            @PathVariable UUID userId) {

        walletService.unfreezeWallet(userId);
        return ResponseEntity.ok(BaseResponse.ok("Wallet unfrozen successfully"));
    }
}
