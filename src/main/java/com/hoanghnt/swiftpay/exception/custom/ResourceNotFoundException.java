package com.hoanghnt.swiftpay.exception.custom;

import com.hoanghnt.swiftpay.exception.ErrorCode;

public class ResourceNotFoundException extends BusinessException {

    public ResourceNotFoundException(String resourceName, Object id) {
        super(ErrorCode.RESOURCE_NOT_FOUND, resourceName + " not found with id: " + id);
    }

    public ResourceNotFoundException(String customMessage) {
        super(ErrorCode.RESOURCE_NOT_FOUND, customMessage);
    }
}