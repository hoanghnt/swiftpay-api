package com.hoanghnt.swiftpay.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hoanghnt.swiftpay.dto.response.ApiResponse;
import com.hoanghnt.swiftpay.service.VNPayService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/payments/vnpay")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final VNPayService vnPayService;

    // IPN: VNPay call server-to-server
    @PostMapping("/ipn")
    public ResponseEntity<Map<String, String>> ipn(
            @RequestParam Map<String, String> params) {
        log.info("VNPay IPN received: {}", params);
        Map<String, String> result = vnPayService.processIpn(params);
        return ResponseEntity.ok(result);
    }

    // Return URL: browser redirect after payment 
    @GetMapping("/return")
    public ResponseEntity<ApiResponse<Map<String, String>>> returnUrl(
            @RequestParam Map<String, String> params) {
        String responseCode = params.get("vnp_ResponseCode");
        if ("00".equals(responseCode)) {
            return ResponseEntity.ok(ApiResponse.success("Payment successful", params));
        }
        return ResponseEntity.ok(ApiResponse.success("Payment failed", params));
    }
}
