package com.hoanghnt.swiftpay.controller;

import com.hoanghnt.swiftpay.dto.request.ForgotPasswordRequest;
import com.hoanghnt.swiftpay.dto.request.LoginRequest;
import com.hoanghnt.swiftpay.dto.request.RefreshTokenRequest;
import com.hoanghnt.swiftpay.dto.request.RegisterRequest;
import com.hoanghnt.swiftpay.dto.request.ResetPasswordRequest;
import com.hoanghnt.swiftpay.dto.response.BaseResponse;
import com.hoanghnt.swiftpay.dto.response.LoginResponse;
import com.hoanghnt.swiftpay.dto.response.RefreshTokenResponse;
import com.hoanghnt.swiftpay.dto.response.RegisterResponse;
import com.hoanghnt.swiftpay.service.AuthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Register, login, email verification, and password reset")
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Register a new account",
               description = "Creates a new user, automatically creates a wallet, and sends a verification email")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Registration successful"),
            @ApiResponse(responseCode = "409", description = "Username or email already exists",
                         content = @Content(schema = @Schema(implementation = BaseResponse.class)))
    })
    @PostMapping("/register")
    public ResponseEntity<BaseResponse<RegisterResponse>> register(
            @Valid @RequestBody RegisterRequest request) {
        RegisterResponse response = authService.register(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(BaseResponse.success("User registered successfully", response));
    }

    @Operation(summary = "Verify email address",
               description = "Validates the token sent to the user's email and activates the account",
               security = {})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Email verified successfully"),
            @ApiResponse(responseCode = "400", description = "Token invalid or expired")
    })
    @GetMapping("/verify")
    public ResponseEntity<BaseResponse<Void>> verifyEmail(@RequestParam String token) {
        authService.verifyEmail(token);
        return ResponseEntity.ok(BaseResponse.ok("Email verified successfully. You can now login."));
    }

    @Operation(summary = "Login",
               description = "Authenticates with username/email and password. Returns access + refresh tokens. Account is locked after 5 failed attempts.",
               security = {})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Login successful"),
            @ApiResponse(responseCode = "401", description = "Invalid credentials"),
            @ApiResponse(responseCode = "423", description = "Account temporarily locked")
    })
    @PostMapping("/login")
    public ResponseEntity<BaseResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(BaseResponse.success("Login successful", response));
    }

    @Operation(summary = "Refresh access token",
               description = "Issues a new access token using a valid refresh token")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token refreshed"),
            @ApiResponse(responseCode = "401", description = "Refresh token invalid or expired")
    })
    @PostMapping("/refresh")
    public ResponseEntity<BaseResponse<RefreshTokenResponse>> refresh(
            @Valid @RequestBody RefreshTokenRequest request) {
        RefreshTokenResponse response = authService.refresh(request.refreshToken());
        return ResponseEntity.ok(BaseResponse.success("Token refreshed", response));
    }

    @Operation(summary = "Logout",
               description = "Invalidates the refresh token stored in Redis")
    @ApiResponse(responseCode = "200", description = "Logged out successfully")
    @PostMapping("/logout")
    public ResponseEntity<BaseResponse<Void>> logout(Authentication authentication) {
        authService.logout(authentication.getName());
        return ResponseEntity.ok(BaseResponse.ok("Logged out successfully"));
    }

    @Operation(summary = "Request password reset",
               description = "Sends a password reset link to the email if it exists. Always returns 200 to prevent user enumeration.",
               security = {})
    @ApiResponse(responseCode = "200", description = "Reset link sent if email exists")
    @PostMapping("/forgot-password")
    public ResponseEntity<BaseResponse<Void>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request.email());
        return ResponseEntity.ok(BaseResponse.ok(
                "If this email exists, a reset link has been sent."));
    }

    @Operation(summary = "Reset password",
               description = "Sets a new password using the token from the reset email. Invalidates all existing sessions.",
               security = {})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Password reset successfully"),
            @ApiResponse(responseCode = "400", description = "Token invalid or expired")
    })
    @PostMapping("/reset-password")
    public ResponseEntity<BaseResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.ok(BaseResponse.ok("Password reset successful. Please login."));
    }
}