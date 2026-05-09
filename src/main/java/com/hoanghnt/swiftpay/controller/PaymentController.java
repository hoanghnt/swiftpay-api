package com.hoanghnt.swiftpay.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hoanghnt.swiftpay.dto.response.BaseResponse;
import com.hoanghnt.swiftpay.service.VNPayService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/payments/vnpay")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Payments", description = "VNPay IPN callback and browser return URL (both public, no JWT required)")
public class PaymentController {

    private final VNPayService vnPayService;

    @Operation(summary = "VNPay IPN callback",
               description = "Server-to-server callback called by VNPay after payment. Verifies HMAC signature and credits the wallet on success. No JWT required.",
               security = {})
    @ApiResponse(responseCode = "200", description = "IPN processed — always returns RspCode 00 on success")
    @PostMapping("/ipn")
    public ResponseEntity<Map<String, String>> ipn(
            @RequestParam Map<String, String> params) {
        log.info("VNPay IPN received: {}", params);
        Map<String, String> result = vnPayService.processIpn(params);
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "VNPay return URL",
               description = "Browser redirect target after the user completes (or cancels) payment on VNPay. For display purposes only — balance is credited via IPN, not here. No JWT required.",
               security = {})
    @ApiResponse(responseCode = "200", description = "Payment result displayed")
    @GetMapping("/return")
    public ResponseEntity<BaseResponse<Map<String, String>>> returnUrl(
            @RequestParam Map<String, String> params) {
        String responseCode = params.get("vnp_ResponseCode");
        if ("00".equals(responseCode)) {
            return ResponseEntity.ok(BaseResponse.success("Payment successful", params));
        }
        return ResponseEntity.ok(BaseResponse.success("Payment failed", params));
    }
}
