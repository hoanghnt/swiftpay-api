package com.hoanghnt.swiftpay.metrics;

import org.springframework.stereotype.Component;

import com.hoanghnt.swiftpay.entity.TransactionStatus;
import com.hoanghnt.swiftpay.repository.TransactionRepository;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

@Component
public class BusinessMetrics {

    private final Counter transferSucceeded;
    private final Counter transferFailed;
    private final Counter reconciliationCompleted;
    private final Counter reconciliationFailed;

    public BusinessMetrics(MeterRegistry registry, TransactionRepository transactionRepository) {
        this.transferSucceeded = Counter.builder("swiftpay.transfer.total")
                .tag("result", "success")
                .description("Số lần transfer hoàn tất (COMPLETED)")
                .register(registry);
        this.transferFailed = Counter.builder("swiftpay.transfer.total")
                .tag("result", "failed")
                .description("Số lần transfer bị wallet-service từ chối nghiệp vụ (FAILED)")
                .register(registry);
        this.reconciliationCompleted = Counter.builder("swiftpay.reconciliation.total")
                .tag("outcome", "completed")
                .description("Record PENDING được reconciliation chuyển sang COMPLETED")
                .register(registry);
        this.reconciliationFailed = Counter.builder("swiftpay.reconciliation.total")
                .tag("outcome", "failed")
                .description("Record PENDING được reconciliation chuyển sang FAILED (quá hard-deadline)")
                .register(registry);

        Gauge.builder("swiftpay.transactions.pending", transactionRepository,
                        repo -> repo.countByStatus(TransactionStatus.PENDING))
                .description("Số transaction đang PENDING (chờ saga hoàn tất hoặc reconciliation chữa)")
                .register(registry);
    }

    public void transferSucceeded() {
        transferSucceeded.increment();
    }

    public void transferFailed() {
        transferFailed.increment();
    }

    public void reconciliationCompleted() {
        reconciliationCompleted.increment();
    }

    public void reconciliationFailed() {
        reconciliationFailed.increment();
    }
}
