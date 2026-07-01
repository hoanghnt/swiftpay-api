package com.hoanghnt.swiftpay.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    VALIDATION_FAILED("VALID_001", "Validation failed", HttpStatus.BAD_REQUEST),
    CONSTRAINT_VIOLATION("VALID_002", "Constraint violation", HttpStatus.BAD_REQUEST),
    INVALID_CREDENTIALS("AUTH_001", "Invalid credentials", HttpStatus.UNAUTHORIZED),
    ACCESS_DENIED("AUTH_004", "Access denied", HttpStatus.FORBIDDEN),
    RESOURCE_NOT_FOUND("RES_001", "Resource not found", HttpStatus.NOT_FOUND),
    BUSINESS_ERROR("BUS_001", "Business rule violated", HttpStatus.BAD_REQUEST),
    INTERNAL_ERROR("SYS_001", "Internal server error", HttpStatus.INTERNAL_SERVER_ERROR),
    USERNAME_ALREADY_EXISTS("AUTH_101", "Username already exists", HttpStatus.CONFLICT),
    EMAIL_ALREADY_EXISTS("AUTH_102", "Email already exists", HttpStatus.CONFLICT),
    PHONE_ALREADY_EXISTS("AUTH_103", "Phone already exists", HttpStatus.CONFLICT),
    INVALID_VERIFICATION_TOKEN("AUTH_201", "Invalid verification token", HttpStatus.BAD_REQUEST),
    VERIFICATION_TOKEN_EXPIRED("AUTH_202", "Verification token expired", HttpStatus.BAD_REQUEST),
    VERIFICATION_TOKEN_ALREADY_USED("AUTH_203", "Verification token already used", HttpStatus.BAD_REQUEST),
    ACCOUNT_LOCKED("AUTH_301", "Account is temporarily locked", HttpStatus.LOCKED),
    ACCOUNT_DISABLED("AUTH_302", "Account is disabled", HttpStatus.FORBIDDEN),
    EMAIL_NOT_VERIFIED("AUTH_303", "Email not verified. Please check your inbox", HttpStatus.FORBIDDEN),
    TOO_MANY_FAILED_ATTEMPTS("AUTH_304", "Too many failed attempts", HttpStatus.TOO_MANY_REQUESTS),
    INVALID_REFRESH_TOKEN("AUTH_401", "Invalid refresh token", HttpStatus.UNAUTHORIZED),
    REFRESH_TOKEN_EXPIRED("AUTH_402", "Refresh token expired", HttpStatus.UNAUTHORIZED),
    INVALID_RESET_TOKEN("AUTH_501", "Invalid or expired reset token", HttpStatus.BAD_REQUEST),
    WALLET_NOT_FOUND("WAL_001", "Wallet not found", HttpStatus.NOT_FOUND),
    WALLET_FROZEN("WAL_002", "Wallet is frozen", HttpStatus.FORBIDDEN),
    INSUFFICIENT_BALANCE("WAL_003", "Insufficient balance", HttpStatus.BAD_REQUEST),
    SELF_TRANSFER_NOT_ALLOWED("WAL_004", "Cannot transfer to yourself", HttpStatus.BAD_REQUEST),
    DUPLICATE_TRANSACTION("TXN_001", "Duplicate transaction", HttpStatus.CONFLICT),
    WALLET_ALREADY_FROZEN("WAL_005", "Wallet is already frozen", HttpStatus.CONFLICT),
    WALLET_NOT_FROZEN("WAL_006", "Wallet is not frozen", HttpStatus.CONFLICT),
    PAYMENT_NOT_FOUND("PAY_001", "Payment transaction not found", HttpStatus.NOT_FOUND),
    PAYMENT_ALREADY_PROCESSED("PAY_002", "Payment already processed", HttpStatus.CONFLICT),
    PAYMENT_EXPIRED("PAY_003", "Payment session expired", HttpStatus.BAD_REQUEST),
    RATE_LIMIT_EXCEEDED("RATE_001", "Too many requests. Please try again later.", HttpStatus.TOO_MANY_REQUESTS),
    TRANSFER_AMOUNT_TOO_LOW("WAL_007", "Transfer amount is below minimum allowed", HttpStatus.BAD_REQUEST),
    TRANSFER_AMOUNT_TOO_HIGH("WAL_008", "Transfer amount exceeds per-transaction limit", HttpStatus.BAD_REQUEST),
    DAILY_TRANSFER_LIMIT_EXCEEDED("WAL_009", "Daily transfer limit exceeded", HttpStatus.BAD_REQUEST),
    USER_NOT_FOUND("USR_001", "User not found", HttpStatus.NOT_FOUND),
    USER_ALREADY_ENABLED("USR_002", "User is already enabled", HttpStatus.CONFLICT),
    USER_ALREADY_DISABLED("USR_003", "User is already disabled", HttpStatus.CONFLICT);

    private final String code;
    private final String defaultMessage;
    private final HttpStatus httpStatus;

    ErrorCode(String code, String defaultMessage, HttpStatus httpStatus) {
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.httpStatus = httpStatus;
    }

    public String getCode() {
        return code;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
