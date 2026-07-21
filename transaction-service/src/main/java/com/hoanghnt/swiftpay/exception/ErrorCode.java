package com.hoanghnt.swiftpay.exception;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    TRANSFER_AMOUNT_TOO_LOW("WAL_007", "Transfer amount is below minimum allowed", HttpStatus.BAD_REQUEST),
    TRANSFER_AMOUNT_TOO_HIGH("WAL_008", "Transfer amount exceeds per-transaction limit", HttpStatus.BAD_REQUEST),
    DAILY_TRANSFER_LIMIT_EXCEEDED("WAL_009", "Daily transfer limit exceeded", HttpStatus.BAD_REQUEST),
    DUPLICATE_TRANSACTION("TXN_001", "Duplicate transaction", HttpStatus.CONFLICT),
    PAYMENT_NOT_FOUND("PAY_001", "Payment transaction not found", HttpStatus.NOT_FOUND),
    PAYMENT_ALREADY_PROCESSED("PAY_002", "Payment already processed", HttpStatus.CONFLICT),
    USER_NOT_FOUND("USR_001", "User not found", HttpStatus.NOT_FOUND),
    RATE_LIMIT_EXCEEDED("RATE_001", "Too many requests. Please try again later.", HttpStatus.TOO_MANY_REQUESTS),
    WALLET_SERVICE_UNAVAILABLE("SVC_001", "Wallet service is temporarily unavailable", HttpStatus.SERVICE_UNAVAILABLE);

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

    private static final Map<String, ErrorCode> BY_CODE =
            Arrays.stream(values()).collect(Collectors.toMap(ErrorCode::getCode, Function.identity()));

    public static ErrorCode fromCode(String code) {
        return code == null ? BUSINESS_ERROR : BY_CODE.getOrDefault(code, BUSINESS_ERROR);
    }
}
