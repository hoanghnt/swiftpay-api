package com.hoanghnt.swiftpay.event;

import com.hoanghnt.swiftpay.entity.Transaction;

public record TransactionCompletedDomainEvent(Transaction transaction) {
}
