package com.hoanghnt.swiftpay.event;

import com.hoanghnt.swiftpay.entity.Transaction;

public record PaymentSucceededDomainEvent(Transaction transaction) {
}
