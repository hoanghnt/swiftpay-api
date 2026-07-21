package com.hoanghnt.swiftpay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

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

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentServiceTest {

    @Mock private TransactionRecordService recordService;
    @Mock private WalletServiceClient walletServiceClient;

    @InjectMocks private PaymentService service;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final String USERNAME = "alice";
    private static final String TXN_REF = "20260720123456789";
    private static final BigDecimal AMOUNT = new BigDecimal("500000");

    private UUID txnId;

    @BeforeEach
    void setUp() {
        txnId = UUID.randomUUID();
        when(recordService.generateTxnRef()).thenReturn(TXN_REF);
        when(recordService.createPendingTopup(any(), any(), any(), any()))
                .thenReturn(Transaction.builder().id(txnId).build());
        when(recordService.findPendingTopup(TXN_REF))
                .thenReturn(new PendingTopup(txnId, USER_ID, AMOUNT));
    }

    @Test
    @DisplayName("BẤT BIẾN: initiate CHỈ tạo bản ghi PENDING — KHÔNG cộng tiền vào ví")
    void initiateDoesNotCreditWallet() {
        TopupInitResult res = service.initiate(USER_ID, USERNAME, AMOUNT);

        verify(walletServiceClient, never()).credit(any());

        assertThat(res.status()).isEqualTo("PENDING");
        assertThat(res.txnRef()).isEqualTo(TXN_REF);
        assertThat(res.amount()).isEqualByComparingTo(AMOUNT);
        assertThat(res.transactionId()).isEqualTo(txnId);
        assertThat(res.paymentUrl()).contains(TXN_REF);
    }

    @Test
    @DisplayName("confirm: cộng ĐÚNG số tiền vào ĐÚNG ví, op_key = txn.id (idempotency phía ví)")
    void confirmCreditsCorrectly() {
        TopupConfirmResult res = service.confirm(TXN_REF);

        ArgumentCaptor<InternalCreditRequest> captor = ArgumentCaptor.forClass(InternalCreditRequest.class);
        verify(walletServiceClient).credit(captor.capture());
        InternalCreditRequest credit = captor.getValue();

        assertThat(credit.userId()).isEqualTo(USER_ID);
        assertThat(credit.amount()).isEqualByComparingTo(AMOUNT);
        assertThat(credit.opKey()).isEqualTo(txnId.toString());

        verify(recordService).markTopupCompleted(txnId);
        assertThat(res.status()).isEqualTo("COMPLETED");
        assertThat(res.amount()).isEqualByComparingTo(AMOUNT);
    }

    @Test
    @DisplayName("confirm: cộng tiền TRƯỚC rồi mới đánh COMPLETED — crash ở giữa thì đối soát chữa được")
    void confirmCreditsBeforeMarkingCompleted() {
        var inOrder = org.mockito.Mockito.inOrder(walletServiceClient, recordService);

        service.confirm(TXN_REF);

        inOrder.verify(walletServiceClient).credit(any());
        inOrder.verify(recordService).markTopupCompleted(txnId);
    }

    @Test
    @DisplayName("confirm: ví báo lỗi nghiệp vụ → đánh FAILED, KHÔNG đánh COMPLETED")
    void confirmWalletBusinessErrorMarksFailed() {
        doThrow(new WalletBusinessException(ErrorCode.WALLET_FROZEN, "frozen"))
                .when(walletServiceClient).credit(any());

        assertThatThrownBy(() -> service.confirm(TXN_REF))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.WALLET_FROZEN);

        verify(recordService).markFailed(eq(txnId), anyString());
        verify(recordService, never()).markTopupCompleted(any());
    }

    @Test
    @DisplayName("confirm: wallet-service chết → để PENDING cho đối soát, KHÔNG đánh FAILED")
    void confirmWalletUnavailableLeavesPending() {
        doThrow(new WalletUnavailableException("timeout"))
                .when(walletServiceClient).credit(any());

        assertThatThrownBy(() -> service.confirm(TXN_REF))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.WALLET_SERVICE_UNAVAILABLE);

        verify(recordService, never()).markFailed(any(), anyString());
        verify(recordService, never()).markTopupCompleted(any());
    }

    @Test
    @DisplayName("cancel: đánh FAILED và KHÔNG đụng tới ví")
    void cancelDoesNotTouchWallet() {
        TopupConfirmResult res = service.cancel(TXN_REF);

        verify(recordService).markFailed(eq(txnId), anyString());
        verify(walletServiceClient, never()).credit(any());
        assertThat(res.status()).isEqualTo("FAILED");
    }
}
