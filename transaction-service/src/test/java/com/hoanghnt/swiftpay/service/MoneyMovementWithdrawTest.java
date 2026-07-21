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
import java.time.LocalDateTime;
import java.util.Optional;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.hoanghnt.swiftpay.audit.AuditService;
import com.hoanghnt.swiftpay.client.WalletBusinessException;
import com.hoanghnt.swiftpay.client.WalletServiceClient;
import com.hoanghnt.swiftpay.client.WalletUnavailableException;
import com.hoanghnt.swiftpay.config.WalletProperties;
import com.hoanghnt.swiftpay.client.dto.InternalDebitRequest;
import com.hoanghnt.swiftpay.dto.request.WithdrawRequest;
import com.hoanghnt.swiftpay.dto.response.TransactionResponse;
import com.hoanghnt.swiftpay.dto.response.WithdrawResponse;
import com.hoanghnt.swiftpay.entity.Transaction;
import com.hoanghnt.swiftpay.exception.ErrorCode;
import com.hoanghnt.swiftpay.exception.custom.BusinessException;
import com.hoanghnt.swiftpay.metrics.BusinessMetrics;
import com.hoanghnt.swiftpay.repository.UserRefRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MoneyMovementWithdrawTest {

    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private WalletProperties walletProperties;
    @Mock private WalletServiceClient walletServiceClient;
    @Mock private TransactionRecordService recordService;
    @Mock private UserRefRepository userRefRepository;
    @Mock private EmailService emailService;
    @Mock private AuditService auditService;
    @Mock private BusinessMetrics businessMetrics;

    @InjectMocks private MoneyMovementService service;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final String USERNAME = "alice";
    private static final String IDEM_KEY = "22222222-3333-4444-5555-666666666666";

    private UUID txnId;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);
        when(walletProperties.getWithdrawFeePercent()).thenReturn(new BigDecimal("0.01"));

        txnId = UUID.randomUUID();
        when(recordService.createPendingWithdraw(any(), any(), any(), any(), any(), any()))
                .thenReturn(Transaction.builder().id(txnId).build());
        when(recordService.toResponseById(any())).thenAnswer(inv -> response(inv.getArgument(0)));
        when(recordService.toResponseByIdempotencyKey(any())).thenAnswer(inv -> response(txnId));
        when(userRefRepository.findById(any())).thenReturn(Optional.empty());
    }

    private static TransactionResponse response(UUID id) {
        return new TransactionResponse(id, "WITHDRAW", "COMPLETED", new BigDecimal("100.0000"),
                BigDecimal.ZERO, USERNAME, null, "withdraw", LocalDateTime.now());
    }

    private WithdrawRequest req(String amount) {
        return new WithdrawRequest(new BigDecimal(amount), "0123456789", "Vietcombank");
    }

    @Test
    @DisplayName("HALF_UP làm tròn LÊN khi chữ số thứ 5 là 5: 100.005 × 1% = 1.00005 → phí 1.0001")
    void feeRoundsHalfUp() {
        WithdrawResponse res = service.withdraw(USER_ID, USERNAME, IDEM_KEY, req("100.005"));

        assertThat(res.fee()).isEqualByComparingTo("1.0001");
        assertThat(res.netAmount()).isEqualByComparingTo("99.0049");
    }

    @Test
    @DisplayName("HALF_UP làm tròn XUỐNG khi chữ số thứ 5 < 5: 100.004 × 1% = 1.00004 → phí 1.0000")
    void feeRoundsDownWhenBelowHalf() {
        WithdrawResponse res = service.withdraw(USER_ID, USERNAME, IDEM_KEY, req("100.004"));

        assertThat(res.fee()).isEqualByComparingTo("1.0000");
        assertThat(res.netAmount()).isEqualByComparingTo("99.004");
    }

    @Test
    @DisplayName("BẤT BIẾN: phí + số thực nhận == số rút, không tiền nào bốc hơi vì làm tròn")
    void feePlusNetAlwaysEqualsAmount() {
        for (String amount : new String[] {"100.005", "100.004", "10000", "33333.3333", "1", "999999.9999"}) {
            WithdrawResponse res = service.withdraw(USER_ID, USERNAME,
                    UUID.randomUUID().toString(), req(amount));

            assertThat(res.fee().add(res.netAmount()))
                    .as("phí + net phải bằng đúng %s", amount)
                    .isEqualByComparingTo(new BigDecimal(amount));
        }
    }

    @Test
    @DisplayName("phí luôn có đúng 4 chữ số thập phân (khớp DECIMAL(19,4) của DB)")
    void feeAlwaysScale4() {
        WithdrawResponse res = service.withdraw(USER_ID, USERNAME, IDEM_KEY, req("33333.3333"));

        assertThat(res.fee().scale()).isEqualTo(4);
    }

    @Test
    @DisplayName("BẤT BIẾN: ví bị trừ số GROSS (số rút), KHÔNG phải net — nếu trừ net là miễn phí cho user")
    void debitsGrossAmountNotNet() {
        service.withdraw(USER_ID, USERNAME, IDEM_KEY, req("100.005"));

        ArgumentCaptor<InternalDebitRequest> captor = ArgumentCaptor.forClass(InternalDebitRequest.class);
        verify(walletServiceClient).debit(captor.capture());
        InternalDebitRequest debit = captor.getValue();

        assertThat(debit.amount()).isEqualByComparingTo("100.005");
        assertThat(debit.amount()).isNotEqualByComparingTo("99.0049");
        assertThat(debit.userId()).isEqualTo(USER_ID);
        assertThat(debit.opKey()).isEqualTo(txnId.toString());
    }

    @Test
    @DisplayName("thành công: đánh COMPLETED KHÔNG phát event (đúng event contract) + lưu idempotency")
    void successMarksCompletedWithoutEvent() {
        service.withdraw(USER_ID, USERNAME, IDEM_KEY, req("10000"));

        verify(recordService).markCompletedNoEvent(txnId);
        verify(valueOps).set(eq("idempotency:" + IDEM_KEY), eq(txnId.toString()), any());
        verify(recordService, never()).markTransferCompleted(any());
    }

    @Test
    @DisplayName("gọi lại cùng idempotency key: trả kết quả cũ, KHÔNG trừ tiền lần hai")
    void idempotentReplayDoesNotDebitAgain() {
        when(valueOps.get("idempotency:" + IDEM_KEY)).thenReturn(txnId.toString());

        WithdrawResponse res = service.withdraw(USER_ID, USERNAME, IDEM_KEY, req("100.005"));

        verify(walletServiceClient, never()).debit(any());
        verify(recordService, never()).createPendingWithdraw(any(), any(), any(), any(), any(), any());
        assertThat(res.fee()).isEqualByComparingTo("1.0001");
    }

    @Test
    @DisplayName("hai request song song cùng idempotency key (DIV): trả bản ghi đã có, KHÔNG trừ tiền")
    void concurrentDuplicateReturnsExisting() {
        when(recordService.createPendingWithdraw(any(), any(), any(), any(), any(), any()))
                .thenThrow(new DataIntegrityViolationException("duplicate idempotency_key"));

        service.withdraw(USER_ID, USERNAME, IDEM_KEY, req("10000"));

        verify(walletServiceClient, never()).debit(any());
        verify(recordService).toResponseByIdempotencyKey(UUID.fromString(IDEM_KEY));
    }

    @Test
    @DisplayName("số dư không đủ (lỗi nghiệp vụ từ ví) → đánh FAILED + trả đúng errorCode")
    void walletBusinessErrorMarksFailed() {
        doThrow(new WalletBusinessException(ErrorCode.INSUFFICIENT_BALANCE, "not enough"))
                .when(walletServiceClient).debit(any());

        assertThatThrownBy(() -> service.withdraw(USER_ID, USERNAME, IDEM_KEY, req("10000")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INSUFFICIENT_BALANCE);

        verify(recordService).markFailed(eq(txnId), anyString());
        verify(recordService, never()).markCompletedNoEvent(any());
    }

    @Test
    @DisplayName("wallet-service chết → để PENDING cho đối soát, KHÔNG đánh FAILED (chưa biết tiền đã trừ chưa)")
    void walletUnavailableLeavesPending() {
        doThrow(new WalletUnavailableException("timeout"))
                .when(walletServiceClient).debit(any());

        assertThatThrownBy(() -> service.withdraw(USER_ID, USERNAME, IDEM_KEY, req("10000")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.WALLET_SERVICE_UNAVAILABLE);

        verify(recordService, never()).markFailed(any(), anyString());
        verify(recordService, never()).markCompletedNoEvent(any());
        verify(valueOps, never()).set(anyString(), anyString(), any());
    }
}
