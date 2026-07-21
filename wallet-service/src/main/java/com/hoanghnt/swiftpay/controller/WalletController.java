package com.hoanghnt.swiftpay.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hoanghnt.swiftpay.dto.response.BaseResponse;
import com.hoanghnt.swiftpay.dto.response.WalletResponse;
import com.hoanghnt.swiftpay.security.AuthPrincipal;
import com.hoanghnt.swiftpay.service.WalletService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/wallets")
@RequiredArgsConstructor
@Tag(name = "Wallet", description = "View own wallet balance and status")
public class WalletController {

    private final WalletService walletService;

    @Operation(summary = "Get my wallet",
               description = "Returns the balance and status of the authenticated user's wallet")
    @GetMapping("/me")
    public ResponseEntity<BaseResponse<WalletResponse>> getMyWallet(Authentication authentication) {
        WalletResponse response = walletService.getMyWallet(AuthPrincipal.userId(authentication));
        return ResponseEntity.ok(BaseResponse.success(response));
    }
}
