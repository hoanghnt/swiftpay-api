package com.hoanghnt.swiftpay.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hoanghnt.swiftpay.dto.response.BaseResponse;
import com.hoanghnt.swiftpay.payment.PaymentGatewayPort;
import com.hoanghnt.swiftpay.payment.TopupConfirmResult;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/mock-payment")
@RequiredArgsConstructor
@Tag(name = "Mock Payment", description = "Self-contained mock top-up gateway — no external credentials required")
public class MockPaymentController {

    private final PaymentGatewayPort paymentGatewayPort;

    @Operation(summary = "Mock payment page", description = "Simple confirm/cancel page for a pending top-up", security = {})
    @ApiResponse(responseCode = "200", description = "HTML page returned")
    @GetMapping("/pay")
    public ResponseEntity<String> pay(@RequestParam String txnRef) {
        String html = """
                <h2>Mock Payment</h2>
                <p>Transaction reference: %s</p>
                <form method="post" action="/api/mock-payment/confirm?txnRef=%s">
                  <button type="submit">Confirm Payment</button>
                </form>
                <form method="post" action="/api/mock-payment/cancel?txnRef=%s">
                  <button type="submit">Cancel</button>
                </form>
                """.formatted(txnRef, txnRef, txnRef);
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html);
    }

    @Operation(summary = "Confirm mock payment", description = "Marks the top-up as completed and credits the wallet", security = {})
    @ApiResponse(responseCode = "200", description = "Payment confirmed")
    @PostMapping("/confirm")
    public ResponseEntity<BaseResponse<TopupConfirmResult>> confirm(@RequestParam String txnRef) {
        TopupConfirmResult result = paymentGatewayPort.confirm(txnRef);
        return ResponseEntity.ok(BaseResponse.success("Payment confirmed", result));
    }

    @Operation(summary = "Cancel mock payment", description = "Marks the top-up as failed", security = {})
    @ApiResponse(responseCode = "200", description = "Payment cancelled")
    @PostMapping("/cancel")
    public ResponseEntity<BaseResponse<TopupConfirmResult>> cancel(@RequestParam String txnRef) {
        TopupConfirmResult result = paymentGatewayPort.cancel(txnRef);
        return ResponseEntity.ok(BaseResponse.success("Payment cancelled", result));
    }
}
