package com.hoanghnt.swiftpay.service;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.hoanghnt.swiftpay.client.WalletBusinessException;
import com.hoanghnt.swiftpay.client.WalletServiceClient;
import com.hoanghnt.swiftpay.client.WalletUnavailableException;
import com.hoanghnt.swiftpay.client.dto.InternalCreditRequest;
import com.hoanghnt.swiftpay.dto.response.TopupConfirmResult;
import com.hoanghnt.swiftpay.dto.response.TopupInitResult;
import com.hoanghnt.swiftpay.entity.Transaction;
import com.hoanghnt.swiftpay.exception.ErrorCode;
import com.hoanghnt.swiftpay.exception.custom.BusinessException;
import com.hoanghnt.swiftpay.service.TransactionRecordService.PendingTopup;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final TransactionRecordService recordService;
    private final WalletServiceClient walletServiceClient;

    public TopupInitResult initiate(UUID userId, String username, BigDecimal amount) {
        // 1. Generate txnRef + create a PENDING transaction
        String txnRef = recordService.generateTxnRef();
        Transaction txn = recordService.createPendingTopup(userId, username, amount, txnRef);

        // 2. Return the mock payment URL for the client to redirect to
        String paymentUrl = "/mock-payment/pay?txnRef=" + txnRef;
        log.info("Mock payment initiated for user={}, txnRef={}", username, txnRef);
        return new TopupInitResult(txn.getId(), txnRef, paymentUrl, amount, "PENDING");
    }

    public TopupConfirmResult confirm(String txnRef) {
        // 1. Look up the PENDING topup by txnRef
        PendingTopup info = recordService.findPendingTopup(txnRef);

        // 2. Call wallet-service credit (idempotent by op_key = txn.id)
        try {
            walletServiceClient.credit(new InternalCreditRequest(
                    info.receiverUserId(), info.amount(), info.txnId().toString()));
        } catch (WalletBusinessException e) {
            recordService.markFailed(info.txnId(), e.getErrorCode().getCode());
            throw new BusinessException(e.getErrorCode(), e.getMessage());
        } catch (WalletUnavailableException e) {
            log.warn("wallet-service unavailable during topup confirm txnId={} — left PENDING for reconciliation",
                    info.txnId(), e);
            throw new BusinessException(ErrorCode.WALLET_SERVICE_UNAVAILABLE);
        }

        // 3. Success → COMPLETED
        recordService.markTopupCompleted(info.txnId());
        log.info("Mock payment confirmed txnRef={}, amount={}", txnRef, info.amount());
        return new TopupConfirmResult(info.txnId(), txnRef, info.amount(), "COMPLETED");
    }

    public TopupConfirmResult cancel(String txnRef) {
        // 1. Look up the PENDING topup → mark FAILED (no wallet call)
        PendingTopup info = recordService.findPendingTopup(txnRef);
        recordService.markFailed(info.txnId(), "Cancelled by user");
        log.info("Mock payment cancelled txnRef={}", txnRef);
        return new TopupConfirmResult(info.txnId(), txnRef, info.amount(), "FAILED");
    }
}
