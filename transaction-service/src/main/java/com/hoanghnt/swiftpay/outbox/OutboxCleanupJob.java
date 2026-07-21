package com.hoanghnt.swiftpay.outbox;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.hoanghnt.swiftpay.repository.OutboxEventRepository;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class OutboxCleanupJob {

    private final OutboxEventRepository repository;
    private final TransactionTemplate txTemplate;

    @Value("${app.outbox.retention-days:7}")
    private int retentionDays;

    @Value("${app.outbox.cleanup-batch-size:1000}")
    private int batchSize;

    private static final int MAX_BATCHES_PER_RUN = 100;

    public OutboxCleanupJob(OutboxEventRepository repository, PlatformTransactionManager txManager) {
        this.repository = repository;
        this.txTemplate = new TransactionTemplate(txManager);
    }

    @Scheduled(cron = "${app.outbox.cleanup-cron:0 30 3 * * *}")
    public void purgePublished() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        int total = 0;

        for (int i = 0; i < MAX_BATCHES_PER_RUN; i++) {
            Integer deleted = txTemplate.execute(status -> repository.deletePublishedBefore(cutoff, batchSize));
            if (deleted == null || deleted == 0) {
                break;
            }
            total += deleted;
        }

        if (total > 0) {
            log.info("Outbox cleanup: đã xóa {} dòng đã publish cũ hơn {} ngày", total, retentionDays);
        }
    }
}
