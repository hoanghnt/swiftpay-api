package com.hoanghnt.swiftpay.client;

import com.hoanghnt.swiftpay.exception.ErrorCode;

public class WalletBusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public WalletBusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
