package com.hoanghnt.swiftpay.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.hoanghnt.swiftpay.client.WalletServiceClient;
import com.hoanghnt.swiftpay.client.WalletUnavailableException;
import com.hoanghnt.swiftpay.config.ReconciliationProperties;
import com.hoanghnt.swiftpay.entity.Transaction;
import com.hoanghnt.swiftpay.entity.TransactionStatus;
import com.hoanghnt.swiftpay.entity.TransactionType;
import com.hoanghnt.swiftpay.metrics.BusinessMetrics;
import com.hoanghnt.swiftpay.repository.TransactionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReconciliationService {

    private final TransactionRepository transactionRepository;
    private final WalletServiceClient walletServiceClient;
    private final TransactionRecordService recordService;
    private final ReconciliationProperties props;
    private final BusinessMetrics businessMetrics;

    @Scheduled(fixedDelayString = "${app.reconciliation.fixed-delay-ms:30000}",
            initialDelayString = "${app.reconciliation.fixed-delay-ms:30000}")
    public void reconcilePendingTransactions() {
        try {
            runReconciliation();
        } catch (Exception e) {
            log.warn("Reconciliation run failed (scheduler kept alive)", e);
        }
    }

    private void runReconciliation() {
        if (!props.isEnabled()) {
            return;
        }

        // 1. Scan for transactions stuck in PENDING longer than stuckAfterSeconds
        LocalDateTime stuckCutoff = LocalDateTime.now().minusSeconds(props.getStuckAfterSeconds());
        List<Transaction> stuck = transactionRepository.findByStatusAndCreatedAtBefore(
                TransactionStatus.PENDING, stuckCutoff);
        if (stuck.isEmpty()) {
            return;
        }
        log.info("Reconciliation: {} PENDING transaction(s) older than {}s", stuck.size(), props.getStuckAfterSeconds());

        // 2. Reconcile each one against wallet-service; a failure on one doesn't block the rest
        LocalDateTime hardFailCutoff = LocalDateTime.now().minusSeconds(props.getHardFailAfterSeconds());
        for (Transaction txn : stuck) {
            try {
                reconcileOne(txn, hardFailCutoff);
            } catch (WalletUnavailableException e) {
                log.warn("Reconciliation: wallet-service unavailable for txnId={}, will retry next run", txn.getId());
            } catch (Exception e) {
                log.warn("Reconciliation: failed to reconcile txnId={}", txn.getId(), e);
            }
        }
    }

    private void reconcileOne(Transaction txn, LocalDateTime hardFailCutoff) {
        // 1. Ask wallet-service whether op_key (= txn.id) was already applied
        boolean applied = walletServiceClient.isOperationApplied(txn.getId().toString());

        // 2a. Already applied → settle as COMPLETED
        if (applied) {
            complete(txn);
            businessMetrics.reconciliationCompleted();
            log.info("Reconciliation: txnId={} type={} → COMPLETED (wallet op was applied)",
                    txn.getId(), txn.getType());
        // 2b. Not applied & past the hard deadline → settle as FAILED
        } else if (txn.getCreatedAt().isBefore(hardFailCutoff)) {
            recordService.markFailed(txn.getId(), "Reconciliation: wallet op not applied within deadline");
            businessMetrics.reconciliationFailed();
            log.info("Reconciliation: txnId={} type={} → FAILED (wallet op never applied, past hard deadline)",
                    txn.getId(), txn.getType());
        }
    }

    private void complete(Transaction txn) {
        TransactionType type = txn.getType();
        if (type == TransactionType.TRANSFER) {
            recordService.markTransferCompleted(txn.getId());
        } else if (type == TransactionType.TOPUP) {
            recordService.markTopupCompleted(txn.getId());
        } else {
            recordService.markCompletedNoEvent(txn.getId());
        }
    }
}
