package com.hoanghnt.swiftpay.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    VALIDATION_FAILED("VALID_001", "Validation failed", HttpStatus.BAD_REQUEST),
    INVALID_CREDENTIALS("AUTH_001", "Invalid credentials", HttpStatus.UNAUTHORIZED),
    ACCESS_DENIED("AUTH_004", "Access denied", HttpStatus.FORBIDDEN),
    INTERNAL_ERROR("SYS_001", "Internal server error", HttpStatus.INTERNAL_SERVER_ERROR),
    METHOD_NOT_ALLOWED("SYS_003", "HTTP method not supported for this endpoint", HttpStatus.METHOD_NOT_ALLOWED),
    ENDPOINT_NOT_FOUND("SYS_004", "Endpoint not found", HttpStatus.NOT_FOUND),
    USERNAME_ALREADY_EXISTS("AUTH_101", "Username already exists", HttpStatus.CONFLICT),
    EMAIL_ALREADY_EXISTS("AUTH_102", "Email already exists", HttpStatus.CONFLICT),
    PHONE_ALREADY_EXISTS("AUTH_103", "Phone already exists", HttpStatus.CONFLICT),
    INVALID_VERIFICATION_TOKEN("AUTH_201", "Invalid verification token", HttpStatus.BAD_REQUEST),
    VERIFICATION_TOKEN_EXPIRED("AUTH_202", "Verification token expired", HttpStatus.BAD_REQUEST),
    VERIFICATION_TOKEN_ALREADY_USED("AUTH_203", "Verification token already used", HttpStatus.BAD_REQUEST),
    ACCOUNT_LOCKED("AUTH_301", "Account is temporarily locked", HttpStatus.LOCKED),
    ACCOUNT_DISABLED("AUTH_302", "Account is disabled", HttpStatus.FORBIDDEN),
    EMAIL_NOT_VERIFIED("AUTH_303", "Email not verified. Please check your inbox", HttpStatus.FORBIDDEN),
    INVALID_REFRESH_TOKEN("AUTH_401", "Invalid refresh token", HttpStatus.UNAUTHORIZED),
    INVALID_RESET_TOKEN("AUTH_501", "Invalid or expired reset token", HttpStatus.BAD_REQUEST),
    RATE_LIMIT_EXCEEDED("RATE_001", "Too many requests. Please try again later.", HttpStatus.TOO_MANY_REQUESTS);

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
