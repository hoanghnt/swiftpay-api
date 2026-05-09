package com.hoanghnt.swiftpay.dto.response;

import java.time.LocalDateTime;

public record BaseResponse<T>(
        boolean success,
        String message,
        T data,
        String errorCode,
        LocalDateTime timestamp
) {

    private static final String DEFAULT_SUCCESS_MESSAGE = "Success";

    public static <T> BaseResponse<T> success(T data) {
        return new BaseResponse<>(true, DEFAULT_SUCCESS_MESSAGE, data, null, LocalDateTime.now());
    }

    public static <T> BaseResponse<T> success(String message, T data) {
        return new BaseResponse<>(true, message, data, null, LocalDateTime.now());
    }

    public static BaseResponse<Void> ok() {
        return new BaseResponse<>(true, DEFAULT_SUCCESS_MESSAGE, null, null, LocalDateTime.now());
    }

    public static BaseResponse<Void> ok(String message) {
        return new BaseResponse<>(true, message, null, null, LocalDateTime.now());
    }

    public static BaseResponse<Void> error(String errorCode, String message) {
        return new BaseResponse<>(false, message, null, errorCode, LocalDateTime.now());
    }
}
