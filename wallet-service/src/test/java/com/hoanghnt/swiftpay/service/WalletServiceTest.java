package com.hoanghnt.swiftpay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hoanghnt.swiftpay.audit.AuditService;
import com.hoanghnt.swiftpay.dto.request.InternalCreditRequest;
import com.hoanghnt.swiftpay.dto.request.InternalDebitRequest;
import com.hoanghnt.swiftpay.dto.request.InternalTransferRequest;
import com.hoanghnt.swiftpay.dto.response.WalletOperationResponse;
import com.hoanghnt.swiftpay.entity.Wallet;
import com.hoanghnt.swiftpay.entity.WalletOperation;
import com.hoanghnt.swiftpay.entity.WalletOperationType;
import com.hoanghnt.swiftpay.exception.ErrorCode;
import com.hoanghnt.swiftpay.exception.custom.BusinessException;
import com.hoanghnt.swiftpay.repository.WalletOperationRepository;
import com.hoanghnt.swiftpay.repository.WalletRepository;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock
    private WalletRepository walletRepository;
    @Mock
    private WalletOperationRepository walletOperationRepository;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private WalletService walletService;

    private static final UUID LOW_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID HIGH_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");

    private static Wallet wallet(UUID userId, String balance, boolean frozen) {
        return Wallet.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .balance(new BigDecimal(balance))
                .currency("VND")
                .frozen(frozen)
                .build();
    }

    private void stubOperationSaveEcho() {
        when(walletOperationRepository.save(any(WalletOperation.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Nested
    @DisplayName("transfer")
    class Transfer {

        @Test
        @DisplayName("thành công: trừ đúng người gửi, cộng đúng người nhận, tổng tiền bảo toàn")
        void transferSuccess() {
            Wallet sender = wallet(LOW_ID, "100000.0000", false);
            Wallet receiver = wallet(HIGH_ID, "20000.0000", false);
            BigDecimal totalBefore = sender.getBalance().add(receiver.getBalance());

            when(walletRepository.findByUserIdWithLock(LOW_ID)).thenReturn(Optional.of(sender));
            when(walletRepository.findByUserIdWithLock(HIGH_ID)).thenReturn(Optional.of(receiver));
            when(walletOperationRepository.findByOpKey("op-1")).thenReturn(Optional.empty());
            stubOperationSaveEcho();

            WalletOperationResponse res = walletService.transfer(
                    new InternalTransferRequest(LOW_ID, HIGH_ID, new BigDecimal("30000"), "op-1"));

            assertThat(sender.getBalance()).isEqualByComparingTo("70000");
            assertThat(receiver.getBalance()).isEqualByComparingTo("50000");
            assertThat(sender.getBalance().add(receiver.getBalance())).isEqualByComparingTo(totalBefore);
            assertThat(res.applied()).isTrue();
            assertThat(res.idempotentReplay()).isFalse();
        }

        @Test
        @DisplayName("khóa ví theo thứ tự UUID TĂNG DẦN dù tham số truyền ngược — chống deadlock")
        void locksInAscendingUuidOrder() {
            Wallet low = wallet(LOW_ID, "0.0000", false);
            Wallet high = wallet(HIGH_ID, "100000.0000", false);

            when(walletRepository.findByUserIdWithLock(LOW_ID)).thenReturn(Optional.of(low));
            when(walletRepository.findByUserIdWithLock(HIGH_ID)).thenReturn(Optional.of(high));
            when(walletOperationRepository.findByOpKey("op-order")).thenReturn(Optional.empty());
            stubOperationSaveEcho();

            walletService.transfer(
                    new InternalTransferRequest(HIGH_ID, LOW_ID, new BigDecimal("1000"), "op-order"));

            InOrder inOrder = Mockito.inOrder(walletRepository);
            inOrder.verify(walletRepository).findByUserIdWithLock(LOW_ID);
            inOrder.verify(walletRepository).findByUserIdWithLock(HIGH_ID);

            assertThat(high.getBalance()).isEqualByComparingTo("99000");
            assertThat(low.getBalance()).isEqualByComparingTo("1000");
        }

        @Test
        @DisplayName("replay cùng op_key: KHÔNG apply lại, không đụng số dư")
        void idempotentReplayDoesNotReapply() {
            Wallet sender = wallet(LOW_ID, "100000.0000", false);
            Wallet receiver = wallet(HIGH_ID, "20000.0000", false);
            WalletOperation applied = WalletOperation.builder()
                    .opKey("op-dup").opType(WalletOperationType.TRANSFER)
                    .fromUserId(LOW_ID).toUserId(HIGH_ID).amount(new BigDecimal("30000")).build();

            when(walletRepository.findByUserIdWithLock(LOW_ID)).thenReturn(Optional.of(sender));
            when(walletRepository.findByUserIdWithLock(HIGH_ID)).thenReturn(Optional.of(receiver));
            when(walletOperationRepository.findByOpKey("op-dup")).thenReturn(Optional.of(applied));

            WalletOperationResponse res = walletService.transfer(
                    new InternalTransferRequest(LOW_ID, HIGH_ID, new BigDecimal("30000"), "op-dup"));

            assertThat(res.idempotentReplay()).isTrue();
            assertThat(sender.getBalance()).isEqualByComparingTo("100000");
            assertThat(receiver.getBalance()).isEqualByComparingTo("20000");
            verify(walletRepository, never()).save(any());
            verify(walletOperationRepository, never()).save(any());
        }

        @Test
        @DisplayName("tự chuyển cho chính mình → SELF_TRANSFER_NOT_ALLOWED, không chạm repository")
        void selfTransferRejected() {
            assertThatThrownBy(() -> walletService.transfer(
                    new InternalTransferRequest(LOW_ID, LOW_ID, new BigDecimal("1000"), "op-self")))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SELF_TRANSFER_NOT_ALLOWED);

            verify(walletRepository, never()).findByUserIdWithLock(any());
        }

        @Test
        @DisplayName("số dư không đủ → INSUFFICIENT_BALANCE và KHÔNG thay đổi số dư nào")
        void insufficientBalance() {
            Wallet sender = wallet(LOW_ID, "500.0000", false);
            Wallet receiver = wallet(HIGH_ID, "20000.0000", false);

            when(walletRepository.findByUserIdWithLock(LOW_ID)).thenReturn(Optional.of(sender));
            when(walletRepository.findByUserIdWithLock(HIGH_ID)).thenReturn(Optional.of(receiver));
            when(walletOperationRepository.findByOpKey("op-poor")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> walletService.transfer(
                    new InternalTransferRequest(LOW_ID, HIGH_ID, new BigDecimal("1000"), "op-poor")))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INSUFFICIENT_BALANCE);

            assertThat(sender.getBalance()).isEqualByComparingTo("500");
            assertThat(receiver.getBalance()).isEqualByComparingTo("20000");
            verify(walletRepository, never()).save(any());
        }

        @Test
        @DisplayName("ví người GỬI bị đóng băng → WALLET_FROZEN")
        void senderFrozen() {
            when(walletRepository.findByUserIdWithLock(LOW_ID))
                    .thenReturn(Optional.of(wallet(LOW_ID, "100000.0000", true)));
            when(walletRepository.findByUserIdWithLock(HIGH_ID))
                    .thenReturn(Optional.of(wallet(HIGH_ID, "0.0000", false)));
            when(walletOperationRepository.findByOpKey("op-fz")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> walletService.transfer(
                    new InternalTransferRequest(LOW_ID, HIGH_ID, new BigDecimal("1000"), "op-fz")))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.WALLET_FROZEN);
        }

        @Test
        @DisplayName("ví người NHẬN bị đóng băng → WALLET_FROZEN (không chỉ kiểm tra người gửi)")
        void receiverFrozen() {
            when(walletRepository.findByUserIdWithLock(LOW_ID))
                    .thenReturn(Optional.of(wallet(LOW_ID, "100000.0000", false)));
            when(walletRepository.findByUserIdWithLock(HIGH_ID))
                    .thenReturn(Optional.of(wallet(HIGH_ID, "0.0000", true)));
            when(walletOperationRepository.findByOpKey("op-fz2")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> walletService.transfer(
                    new InternalTransferRequest(LOW_ID, HIGH_ID, new BigDecimal("1000"), "op-fz2")))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.WALLET_FROZEN);
        }

        @Test
        @DisplayName("không tìm thấy ví → WALLET_NOT_FOUND")
        void walletNotFound() {
            when(walletRepository.findByUserIdWithLock(LOW_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> walletService.transfer(
                    new InternalTransferRequest(LOW_ID, HIGH_ID, new BigDecimal("1000"), "op-404")))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.WALLET_NOT_FOUND);
        }

        @Test
        @DisplayName("số học tiền chính xác tới 4 chữ số thập phân, không sai số kiểu float")
        void moneyArithmeticIsExact() {
            Wallet sender = wallet(LOW_ID, "0.3000", false);
            Wallet receiver = wallet(HIGH_ID, "0.0000", false);

            when(walletRepository.findByUserIdWithLock(LOW_ID)).thenReturn(Optional.of(sender));
            when(walletRepository.findByUserIdWithLock(HIGH_ID)).thenReturn(Optional.of(receiver));
            when(walletOperationRepository.findByOpKey("op-prec")).thenReturn(Optional.empty());
            stubOperationSaveEcho();

            walletService.transfer(new InternalTransferRequest(LOW_ID, HIGH_ID, new BigDecimal("0.1000"), "op-prec"));
            when(walletOperationRepository.findByOpKey("op-prec2")).thenReturn(Optional.empty());
            walletService.transfer(new InternalTransferRequest(LOW_ID, HIGH_ID, new BigDecimal("0.1000"), "op-prec2"));

            assertThat(sender.getBalance()).isEqualByComparingTo("0.1");
            assertThat(receiver.getBalance()).isEqualByComparingTo("0.2");
        }

        @Test
        @DisplayName("op_key được ghi lại đúng loại TRANSFER kèm from/to/amount (dấu vết đối soát)")
        void recordsOperationForReconciliation() {
            when(walletRepository.findByUserIdWithLock(LOW_ID))
                    .thenReturn(Optional.of(wallet(LOW_ID, "100000.0000", false)));
            when(walletRepository.findByUserIdWithLock(HIGH_ID))
                    .thenReturn(Optional.of(wallet(HIGH_ID, "0.0000", false)));
            when(walletOperationRepository.findByOpKey("op-rec")).thenReturn(Optional.empty());
            stubOperationSaveEcho();

            walletService.transfer(
                    new InternalTransferRequest(LOW_ID, HIGH_ID, new BigDecimal("2500"), "op-rec"));

            ArgumentCaptor<WalletOperation> captor = ArgumentCaptor.forClass(WalletOperation.class);
            verify(walletOperationRepository).save(captor.capture());
            WalletOperation op = captor.getValue();
            assertThat(op.getOpKey()).isEqualTo("op-rec");
            assertThat(op.getOpType()).isEqualTo(WalletOperationType.TRANSFER);
            assertThat(op.getFromUserId()).isEqualTo(LOW_ID);
            assertThat(op.getToUserId()).isEqualTo(HIGH_ID);
            assertThat(op.getAmount()).isEqualByComparingTo("2500");
        }
    }

    @Nested
    @DisplayName("credit (top-up)")
    class Credit {

        @Test
        @DisplayName("cộng đúng số tiền vào ví")
        void creditAddsBalance() {
            Wallet w = wallet(LOW_ID, "1000.0000", false);
            when(walletRepository.findByUserIdWithLock(LOW_ID)).thenReturn(Optional.of(w));
            when(walletOperationRepository.findByOpKey("cr-1")).thenReturn(Optional.empty());
            stubOperationSaveEcho();

            WalletOperationResponse res = walletService.credit(
                    new InternalCreditRequest(LOW_ID, new BigDecimal("50000"), "cr-1"));

            assertThat(w.getBalance()).isEqualByComparingTo("51000");
            assertThat(res.applied()).isTrue();
            assertThat(res.idempotentReplay()).isFalse();
        }

        @Test
        @DisplayName("replay cùng op_key: KHÔNG cộng tiền lần hai (chống nạp trùng)")
        void creditIdempotent() {
            Wallet w = wallet(LOW_ID, "1000.0000", false);
            WalletOperation applied = WalletOperation.builder()
                    .opKey("cr-dup").opType(WalletOperationType.CREDIT)
                    .toUserId(LOW_ID).amount(new BigDecimal("50000")).build();

            when(walletRepository.findByUserIdWithLock(LOW_ID)).thenReturn(Optional.of(w));
            when(walletOperationRepository.findByOpKey("cr-dup")).thenReturn(Optional.of(applied));

            WalletOperationResponse res = walletService.credit(
                    new InternalCreditRequest(LOW_ID, new BigDecimal("50000"), "cr-dup"));

            assertThat(res.idempotentReplay()).isTrue();
            assertThat(w.getBalance()).isEqualByComparingTo("1000");
            verify(walletRepository, never()).save(any());
        }

        @Test
        @DisplayName("không tìm thấy ví → WALLET_NOT_FOUND")
        void creditWalletNotFound() {
            when(walletRepository.findByUserIdWithLock(LOW_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> walletService.credit(
                    new InternalCreditRequest(LOW_ID, new BigDecimal("1000"), "cr-404")))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.WALLET_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("debit (withdraw)")
    class Debit {

        @Test
        @DisplayName("trừ đúng số tiền")
        void debitSubtracts() {
            Wallet w = wallet(LOW_ID, "10000.0000", false);
            when(walletRepository.findByUserIdWithLock(LOW_ID)).thenReturn(Optional.of(w));
            when(walletOperationRepository.findByOpKey("db-1")).thenReturn(Optional.empty());
            stubOperationSaveEcho();

            walletService.debit(new InternalDebitRequest(LOW_ID, new BigDecimal("2500"), "db-1"));

            assertThat(w.getBalance()).isEqualByComparingTo("7500");
        }

        @Test
        @DisplayName("số dư không đủ → INSUFFICIENT_BALANCE, số dư giữ nguyên (không âm)")
        void debitInsufficient() {
            Wallet w = wallet(LOW_ID, "100.0000", false);
            when(walletRepository.findByUserIdWithLock(LOW_ID)).thenReturn(Optional.of(w));
            when(walletOperationRepository.findByOpKey("db-poor")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> walletService.debit(
                    new InternalDebitRequest(LOW_ID, new BigDecimal("500"), "db-poor")))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INSUFFICIENT_BALANCE);

            assertThat(w.getBalance()).isEqualByComparingTo("100");
            assertThat(w.getBalance()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
            verify(walletRepository, never()).save(any());
        }

        @Test
        @DisplayName("ví đóng băng → WALLET_FROZEN")
        void debitFrozen() {
            Wallet w = wallet(LOW_ID, "10000.0000", true);
            when(walletRepository.findByUserIdWithLock(LOW_ID)).thenReturn(Optional.of(w));
            when(walletOperationRepository.findByOpKey("db-fz")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> walletService.debit(
                    new InternalDebitRequest(LOW_ID, new BigDecimal("100"), "db-fz")))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.WALLET_FROZEN);

            assertThat(w.getBalance()).isEqualByComparingTo("10000");
        }

        @Test
        @DisplayName("replay cùng op_key: KHÔNG trừ lần hai")
        void debitIdempotent() {
            Wallet w = wallet(LOW_ID, "10000.0000", false);
            WalletOperation applied = WalletOperation.builder()
                    .opKey("db-dup").opType(WalletOperationType.DEBIT)
                    .fromUserId(LOW_ID).amount(new BigDecimal("2500")).build();

            when(walletRepository.findByUserIdWithLock(LOW_ID)).thenReturn(Optional.of(w));
            when(walletOperationRepository.findByOpKey("db-dup")).thenReturn(Optional.of(applied));

            WalletOperationResponse res = walletService.debit(
                    new InternalDebitRequest(LOW_ID, new BigDecimal("2500"), "db-dup"));

            assertThat(res.idempotentReplay()).isTrue();
            assertThat(w.getBalance()).isEqualByComparingTo("10000");
            verify(walletRepository, never()).save(any());
        }

        @Test
        @DisplayName("kiểm tra idempotency TRƯỚC kiểm tra frozen — replay của ví sau đó bị đóng băng vẫn trả về được")
        void idempotencyCheckedBeforeFrozenCheck() {
            Wallet w = wallet(LOW_ID, "7500.0000", true);
            WalletOperation applied = WalletOperation.builder()
                    .opKey("db-late").opType(WalletOperationType.DEBIT)
                    .fromUserId(LOW_ID).amount(new BigDecimal("2500")).build();

            when(walletRepository.findByUserIdWithLock(LOW_ID)).thenReturn(Optional.of(w));
            when(walletOperationRepository.findByOpKey("db-late")).thenReturn(Optional.of(applied));

            WalletOperationResponse res = walletService.debit(
                    new InternalDebitRequest(LOW_ID, new BigDecimal("2500"), "db-late"));

            assertThat(res.idempotentReplay()).isTrue();
        }
    }

    @Nested
    @DisplayName("freeze / unfreeze")
    class FreezeUnfreeze {

        @Test
        @DisplayName("đóng băng ví chưa bị đóng băng")
        void freeze() {
            Wallet w = wallet(LOW_ID, "1000.0000", false);
            when(walletRepository.findByUserId(LOW_ID)).thenReturn(Optional.of(w));

            walletService.freezeWallet(LOW_ID);

            assertThat(w.isFrozen()).isTrue();
            verify(walletRepository).save(w);
        }

        @Test
        @DisplayName("đóng băng ví đã đóng băng → WALLET_ALREADY_FROZEN")
        void freezeAlreadyFrozen() {
            when(walletRepository.findByUserId(LOW_ID))
                    .thenReturn(Optional.of(wallet(LOW_ID, "1000.0000", true)));

            assertThatThrownBy(() -> walletService.freezeWallet(LOW_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.WALLET_ALREADY_FROZEN);
        }

        @Test
        @DisplayName("gỡ đóng băng ví không bị đóng băng → WALLET_NOT_FROZEN")
        void unfreezeNotFrozen() {
            when(walletRepository.findByUserId(LOW_ID))
                    .thenReturn(Optional.of(wallet(LOW_ID, "1000.0000", false)));

            assertThatThrownBy(() -> walletService.unfreezeWallet(LOW_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.WALLET_NOT_FROZEN);
        }
    }
}
