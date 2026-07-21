package com.hoanghnt.swiftpay.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import com.hoanghnt.swiftpay.audit.AuditService;
import com.hoanghnt.swiftpay.client.WalletBusinessException;
import com.hoanghnt.swiftpay.client.WalletServiceClient;
import com.hoanghnt.swiftpay.client.WalletUnavailableException;
import com.hoanghnt.swiftpay.config.WalletProperties;
import com.hoanghnt.swiftpay.dto.request.TransferRequest;
import com.hoanghnt.swiftpay.entity.Transaction;
import com.hoanghnt.swiftpay.entity.UserRef;
import com.hoanghnt.swiftpay.exception.ErrorCode;
import com.hoanghnt.swiftpay.exception.custom.BusinessException;
import com.hoanghnt.swiftpay.exception.custom.ResourceNotFoundException;
import com.hoanghnt.swiftpay.metrics.BusinessMetrics;
import com.hoanghnt.swiftpay.repository.UserRefRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MoneyMovementServiceTest {

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

    private static final UUID SENDER_ID = UUID.randomUUID();
    private static final UUID RECEIVER_ID = UUID.randomUUID();
    private static final String SENDER = "alice";
    private static final String RECEIVER = "bob";
    private static final String IDEM_KEY = "11111111-2222-3333-4444-555555555555";

    private UUID txnId;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);

        when(walletProperties.getMinTransferAmount()).thenReturn(new BigDecimal("1000"));
        when(walletProperties.getMaxTransferAmount()).thenReturn(new BigDecimal("10000000"));
        when(walletProperties.getMaxDailyTransfer()).thenReturn(new BigDecimal("50000000"));

        UserRef receiver = new UserRef();
        receiver.setId(RECEIVER_ID);
        receiver.setUsername(RECEIVER);
        receiver.setEmail("bob@test.local");
        when(userRefRepository.findByUsername(RECEIVER)).thenReturn(Optional.of(receiver));

        txnId = UUID.randomUUID();
        when(recordService.createPendingTransfer(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Transaction.builder().id(txnId).build());

        reserveReturns(30000L * 10000);
    }

    private void reserveReturns(long value) {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any(), any()))
                .thenReturn(value);
    }

    private TransferRequest req(String amount) {
        return new TransferRequest(RECEIVER, new BigDecimal(amount), "test");
    }

    private static long scaled(String amount) {
        return new BigDecimal(amount).movePointRight(4).longValueExact();
    }

    @Test
    @DisplayName("thành công: reserve hạn mức → tạo PENDING → gọi wallet → COMPLETED → lưu idempotency")
    void happyPath() {
        service.transfer(SENDER_ID, SENDER, IDEM_KEY, req("30000"));

        verify(walletServiceClient).transfer(any());
        verify(recordService).markTransferCompleted(txnId);
        verify(businessMetrics).transferSucceeded();
        verify(valueOps).set(eq("idempotency:" + IDEM_KEY), eq(txnId.toString()), any());
        verify(valueOps, never()).decrement(anyString(), anyLong());
    }

    @Test
    @DisplayName("gọi lại cùng idempotency key: trả kết quả cũ, KHÔNG chuyển tiền lần hai")
    void idempotentEdgeReturnsCached() {
        when(valueOps.get("idempotency:" + IDEM_KEY)).thenReturn(txnId.toString());

        service.transfer(SENDER_ID, SENDER, IDEM_KEY, req("30000"));

        verify(recordService).toResponseById(txnId);
        verify(walletServiceClient, never()).transfer(any());
        verify(recordService, never()).createPendingTransfer(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("số tiền dưới mức tối thiểu → từ chối, không reserve hạn mức, không tạo giao dịch")
    void amountTooLow() {
        assertThatThrownBy(() -> service.transfer(SENDER_ID, SENDER, IDEM_KEY, req("999")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TRANSFER_AMOUNT_TOO_LOW);

        verify(redisTemplate, never()).execute(any(RedisScript.class), anyList(), any(), any(), any());
        verify(walletServiceClient, never()).transfer(any());
    }

    @Test
    @DisplayName("số tiền vượt mức tối đa mỗi giao dịch → từ chối")
    void amountTooHigh() {
        assertThatThrownBy(() -> service.transfer(SENDER_ID, SENDER, IDEM_KEY, req("10000001")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TRANSFER_AMOUNT_TOO_HIGH);

        verify(walletServiceClient, never()).transfer(any());
    }

    @Test
    @DisplayName("tự chuyển cho chính mình → từ chối trước khi chạm tới ví")
    void selfTransfer() {
        TransferRequest toSelf = new TransferRequest(SENDER, new BigDecimal("30000"), "self");

        assertThatThrownBy(() -> service.transfer(SENDER_ID, SENDER, IDEM_KEY, toSelf))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SELF_TRANSFER_NOT_ALLOWED);

        verify(walletServiceClient, never()).transfer(any());
    }

    @Test
    @DisplayName("người nhận không tồn tại → ResourceNotFound, không reserve hạn mức")
    void receiverNotFound() {
        when(userRefRepository.findByUsername("ghost")).thenReturn(Optional.empty());
        TransferRequest toGhost = new TransferRequest("ghost", new BigDecimal("30000"), "x");

        assertThatThrownBy(() -> service.transfer(SENDER_ID, SENDER, IDEM_KEY, toGhost))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(redisTemplate, never()).execute(any(RedisScript.class), anyList(), any(), any(), any());
    }

    @Test
    @DisplayName("chạm hạn mức ngày (script trả -1) → từ chối, KHÔNG tạo giao dịch")
    void dailyLimitExceeded() {
        reserveReturns(-1L);

        assertThatThrownBy(() -> service.transfer(SENDER_ID, SENDER, IDEM_KEY, req("30000")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DAILY_TRANSFER_LIMIT_EXCEEDED);

        verify(valueOps, never()).decrement(anyString(), anyLong());
        verify(recordService, never()).createPendingTransfer(any(), any(), any(), any(), any(), any(), any());
        verify(walletServiceClient, never()).transfer(any());
    }

    @Test
    @DisplayName("wallet-service báo lỗi nghiệp vụ → NHẢ hạn mức + đánh FAILED")
    void releasesDailyOnWalletBusinessError() {
        doThrow(new WalletBusinessException(ErrorCode.INSUFFICIENT_BALANCE, "not enough"))
                .when(walletServiceClient).transfer(any());

        assertThatThrownBy(() -> service.transfer(SENDER_ID, SENDER, IDEM_KEY, req("30000")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INSUFFICIENT_BALANCE);

        verify(valueOps).decrement(anyString(), eq(scaled("30000")));
        verify(recordService).markFailed(eq(txnId), anyString());
        verify(businessMetrics).transferFailed();
    }

    @Test
    @DisplayName("wallet-service chết → NHẢ hạn mức, để PENDING cho đối soát (KHÔNG đánh FAILED)")
    void releasesDailyOnWalletUnavailable() {
        doThrow(new WalletUnavailableException("timeout"))
                .when(walletServiceClient).transfer(any());

        assertThatThrownBy(() -> service.transfer(SENDER_ID, SENDER, IDEM_KEY, req("30000")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.WALLET_SERVICE_UNAVAILABLE);

        verify(valueOps).decrement(anyString(), eq(scaled("30000")));
        verify(recordService, never()).markFailed(any(), anyString());
        verify(recordService, never()).markTransferCompleted(any());
    }

    @Test
    @DisplayName("hai request cùng idempotency key chạy song song (DIV) → NHẢ hạn mức, trả giao dịch đã có")
    void releasesDailyOnConcurrentDuplicate() {
        when(recordService.createPendingTransfer(any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new DataIntegrityViolationException("duplicate idempotency_key"));

        assertThatCode(() -> service.transfer(SENDER_ID, SENDER, IDEM_KEY, req("30000")))
                .doesNotThrowAnyException();

        verify(valueOps).decrement(anyString(), eq(scaled("30000")));
        verify(recordService).toResponseByIdempotencyKey(UUID.fromString(IDEM_KEY));
        verify(walletServiceClient, never()).transfer(any());
    }

    @Test
    @DisplayName("số tiền nhả đúng bằng số đã reserve, ở dạng scaled ×10^4 (không dùng float cho tiền)")
    void releasedAmountMatchesReservedExactly() {
        doThrow(new WalletUnavailableException("down")).when(walletServiceClient).transfer(any());

        assertThatThrownBy(() -> service.transfer(SENDER_ID, SENDER, IDEM_KEY, req("12345.6789")))
                .isInstanceOf(BusinessException.class);

        verify(valueOps).decrement(anyString(), eq(123456789L));
    }
}
