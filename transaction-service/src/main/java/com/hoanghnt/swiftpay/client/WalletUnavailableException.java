package com.hoanghnt.swiftpay.client;

public class WalletUnavailableException extends RuntimeException {

    public WalletUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public WalletUnavailableException(String message) {
        super(message);
    }
}
