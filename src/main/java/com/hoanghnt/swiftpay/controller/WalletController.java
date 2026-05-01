package com.hoanghnt.swiftpay.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hoanghnt.swiftpay.dto.request.TopupRequest;
import com.hoanghnt.swiftpay.dto.response.ApiResponse;
import com.hoanghnt.swiftpay.dto.response.TopupResponse;
import com.hoanghnt.swiftpay.dto.response.WalletResponse;
import com.hoanghnt.swiftpay.service.VNPayService;
import com.hoanghnt.swiftpay.service.WalletService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;
    private final VNPayService vnPayService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<WalletResponse>> getMyWallet(Authentication authentication) {
        WalletResponse response = walletService.getMyWallet(authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/topup")
    public ResponseEntity<ApiResponse<TopupResponse>> topup(
            Authentication authentication,
            @Valid @RequestBody TopupRequest request,
            HttpServletRequest httpRequest) {

        String ipAddress = httpRequest.getHeader("X-Forwarded-For");
        if (ipAddress == null) {
            ipAddress = httpRequest.getRemoteAddr();
        }

        TopupResponse response = vnPayService.createPaymentUrl(
                authentication.getName(), request, ipAddress);

        return ResponseEntity.ok(ApiResponse.success("Payment URL created", response));
    }
}
