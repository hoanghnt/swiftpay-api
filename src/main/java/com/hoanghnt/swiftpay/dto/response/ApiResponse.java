package com.hoanghnt.swiftpay.dto.response;

import java.time.LocalDateTime;

public record ApiResponse<T>(
        boolean success,
        String message,
        T data,
        String errorCode,
        LocalDateTime timestamp
) {

    private static final String DEFAULT_SUCCESS_MESSAGE = "Success";

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, DEFAULT_SUCCESS_MESSAGE, data, null, LocalDateTime.now());
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data, null, LocalDateTime.now());
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(true, DEFAULT_SUCCESS_MESSAGE, null, null, LocalDateTime.now());
    }

    public static ApiResponse<Void> ok(String message) {
        return new ApiResponse<>(true, message, null, null, LocalDateTime.now());
    }

    public static ApiResponse<Void> error(String errorCode, String message) {
        return new ApiResponse<>(false, message, null, errorCode, LocalDateTime.now());
    }
}
