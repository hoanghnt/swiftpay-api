package com.hoanghnt.swiftpay.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    VALIDATION_FAILED("VALID_001", "Validation failed", HttpStatus.BAD_REQUEST),
    CONSTRAINT_VIOLATION("VALID_002", "Constraint violation", HttpStatus.BAD_REQUEST),
    ACCESS_DENIED("AUTH_004", "Access denied", HttpStatus.FORBIDDEN),
    UNAUTHORIZED("AUTH_002", "Authentication required", HttpStatus.UNAUTHORIZED),
    RESOURCE_NOT_FOUND("RES_001", "Resource not found", HttpStatus.NOT_FOUND),
    BUSINESS_ERROR("BUS_001", "Business rule violated", HttpStatus.BAD_REQUEST),
    INTERNAL_ERROR("SYS_001", "Internal server error", HttpStatus.INTERNAL_SERVER_ERROR),
    METHOD_NOT_ALLOWED("SYS_003", "HTTP method not supported for this endpoint", HttpStatus.METHOD_NOT_ALLOWED),
    ENDPOINT_NOT_FOUND("SYS_004", "Endpoint not found", HttpStatus.NOT_FOUND),
    WALLET_NOT_FOUND("WAL_001", "Wallet not found", HttpStatus.NOT_FOUND),
    WALLET_FROZEN("WAL_002", "Wallet is frozen", HttpStatus.BAD_REQUEST),
    INSUFFICIENT_BALANCE("WAL_003", "Insufficient balance", HttpStatus.BAD_REQUEST),
    SELF_TRANSFER_NOT_ALLOWED("WAL_004", "Cannot transfer to yourself", HttpStatus.BAD_REQUEST),
    WALLET_ALREADY_FROZEN("WAL_005", "Wallet is already frozen", HttpStatus.CONFLICT),
    WALLET_NOT_FROZEN("WAL_006", "Wallet is not frozen", HttpStatus.CONFLICT),
    USER_NOT_FOUND("USR_001", "User not found", HttpStatus.NOT_FOUND);

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
