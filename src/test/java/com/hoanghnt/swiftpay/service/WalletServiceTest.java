package com.hoanghnt.swiftpay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.hoanghnt.swiftpay.audit.AuditService;
import com.hoanghnt.swiftpay.config.WalletProperties;
import com.hoanghnt.swiftpay.dto.request.TransferRequest;
import com.hoanghnt.swiftpay.dto.request.WithdrawRequest;
import com.hoanghnt.swiftpay.dto.response.TransactionResponse;
import com.hoanghnt.swiftpay.dto.response.WithdrawResponse;
import com.hoanghnt.swiftpay.entity.Transaction;
import com.hoanghnt.swiftpay.entity.TransactionStatus;
import com.hoanghnt.swiftpay.entity.TransactionType;
import com.hoanghnt.swiftpay.entity.User;
import com.hoanghnt.swiftpay.entity.Wallet;
import com.hoanghnt.swiftpay.exception.ErrorCode;
import com.hoanghnt.swiftpay.exception.custom.BusinessException;
import com.hoanghnt.swiftpay.repository.TransactionRepository;
import com.hoanghnt.swiftpay.repository.UserRepository;
import com.hoanghnt.swiftpay.repository.WalletRepository;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock UserRepository userRepository;
    @Mock WalletRepository walletRepository;
    @Mock TransactionRepository transactionRepository;
    @Mock RedisTemplate<String, String> redisTemplate;
    @Mock ValueOperations<String, String> valueOps;
    @Mock EmailService emailService;
    @Mock AuditService auditService;
    @Mock WalletProperties walletProperties;

    @InjectMocks WalletService walletService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        lenient().when(walletProperties.getMinTransferAmount()).thenReturn(new BigDecimal("1000"));
        lenient().when(walletProperties.getMaxTransferAmount()).thenReturn(new BigDecimal("10000000"));
        lenient().when(walletProperties.getMaxDailyTransfer()).thenReturn(new BigDecimal("50000000"));
        lenient().when(walletProperties.getWithdrawFeePercent()).thenReturn(new BigDecimal("0.01"));
        // JPA @GeneratedValue only assigns an id on actual persistence; mocked save() must do it manually
        lenient().when(transactionRepository.save(any())).thenAnswer(inv -> {
            Transaction txn = inv.getArgument(0);
            if (txn.getId() == null) {
                txn.setId(UUID.randomUUID());
            }
            return txn;
        });
    }

    private User buildUser(String username) {
        return User.builder()
            .id(UUID.randomUUID())
            .username(username)
            .email(username + "@test.com")
            .emailVerified(true)
            .enabled(true)
            .build();
    }

    private Wallet buildWallet(User user, BigDecimal balance) {
        return Wallet.builder()
            .id(UUID.randomUUID())
            .user(user)
            .balance(balance)
            .currency("VND")
            .frozen(false)
            .version(0L)
            .build();
    }

    // ==================== transfer() ====================

    @Test
    void transfer_whenSufficientBalance_shouldDebitSenderAndCreditReceiver() {
        User sender = buildUser("alice");
        User receiver = buildUser("bob");
        Wallet senderWallet = buildWallet(sender, new BigDecimal("1000000"));
        Wallet receiverWallet = buildWallet(receiver, BigDecimal.ZERO);
        TransferRequest request = new TransferRequest("bob", new BigDecimal("500000"), "test transfer");
        String idempotencyKey = UUID.randomUUID().toString();

        when(valueOps.get(anyString())).thenReturn(null);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(sender));
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(receiver));
        mockWalletLockOrder(sender, senderWallet, receiver, receiverWallet);

        TransactionResponse result = walletService.transfer("alice", idempotencyKey, request);

        assertThat(senderWallet.getBalance()).isEqualByComparingTo("500000");
        assertThat(receiverWallet.getBalance()).isEqualByComparingTo("500000");
        assertThat(result.amount()).isEqualByComparingTo("500000");
        verify(transactionRepository).save(argThatTxn(
                txn -> txn.getType() == TransactionType.TRANSFER
                        && txn.getStatus() == TransactionStatus.COMPLETED
                        && txn.getAmount().compareTo(new BigDecimal("500000")) == 0));
        verify(valueOps).set(eq("idempotency:" + idempotencyKey), any(), any(Duration.class));
    }

    @Test
    void transfer_whenIdempotencyKeyCached_shouldReturnCachedTransactionWithoutReprocessing() {
        Transaction cached = Transaction.builder()
                .id(UUID.randomUUID())
                .type(TransactionType.TRANSFER)
                .status(TransactionStatus.COMPLETED)
                .amount(new BigDecimal("100000"))
                .build();
        String idempotencyKey = UUID.randomUUID().toString();

        when(valueOps.get("idempotency:" + idempotencyKey)).thenReturn(cached.getId().toString());
        when(transactionRepository.findById(cached.getId())).thenReturn(Optional.of(cached));

        TransactionResponse result = walletService.transfer(
                "alice", idempotencyKey, new TransferRequest("bob", new BigDecimal("100000"), null));

        assertThat(result.transactionId()).isEqualTo(cached.getId());
        verify(userRepository, org.mockito.Mockito.never()).findByUsername(anyString());
        verify(transactionRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void transfer_whenSelfTransfer_shouldThrowSelfTransferNotAllowed() {
        User sender = buildUser("alice");
        when(valueOps.get(anyString())).thenReturn(null);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(sender));

        assertThatThrownBy(() -> walletService.transfer(
                "alice", UUID.randomUUID().toString(), new TransferRequest("alice", new BigDecimal("100000"), null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.SELF_TRANSFER_NOT_ALLOWED));
    }

    @Test
    void transfer_whenSenderWalletFrozen_shouldThrowWalletFrozen() {
        User sender = buildUser("alice");
        User receiver = buildUser("bob");
        Wallet senderWallet = buildWallet(sender, new BigDecimal("1000000"));
        senderWallet.setFrozen(true);
        Wallet receiverWallet = buildWallet(receiver, BigDecimal.ZERO);

        when(valueOps.get(anyString())).thenReturn(null);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(sender));
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(receiver));
        mockWalletLockOrder(sender, senderWallet, receiver, receiverWallet);

        assertThatThrownBy(() -> walletService.transfer(
                "alice", UUID.randomUUID().toString(), new TransferRequest("bob", new BigDecimal("100000"), null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.WALLET_FROZEN));
    }

    @Test
    void transfer_whenReceiverWalletFrozen_shouldThrowWalletFrozen() {
        User sender = buildUser("alice");
        User receiver = buildUser("bob");
        Wallet senderWallet = buildWallet(sender, new BigDecimal("1000000"));
        Wallet receiverWallet = buildWallet(receiver, BigDecimal.ZERO);
        receiverWallet.setFrozen(true);

        when(valueOps.get(anyString())).thenReturn(null);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(sender));
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(receiver));
        mockWalletLockOrder(sender, senderWallet, receiver, receiverWallet);

        assertThatThrownBy(() -> walletService.transfer(
                "alice", UUID.randomUUID().toString(), new TransferRequest("bob", new BigDecimal("100000"), null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.WALLET_FROZEN));
    }

    @Test
    void transfer_whenInsufficientBalance_shouldThrowBusinessException() {
        User sender = buildUser("alice");
        User receiver = buildUser("bob");
        Wallet senderWallet = buildWallet(sender, new BigDecimal("100"));
        Wallet receiverWallet = buildWallet(receiver, BigDecimal.ZERO);

        when(valueOps.get(anyString())).thenReturn(null);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(sender));
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(receiver));
        mockWalletLockOrder(sender, senderWallet, receiver, receiverWallet);

        assertThatThrownBy(() -> walletService.transfer(
                "alice", UUID.randomUUID().toString(), new TransferRequest("bob", new BigDecimal("50000"), "test")))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.INSUFFICIENT_BALANCE));
    }

    @Test
    void transfer_shouldLockWalletsInConsistentDeterministicOrder() {
        User sender = buildUser("alice");
        User receiver = buildUser("bob");
        Wallet senderWallet = buildWallet(sender, new BigDecimal("1000000"));
        Wallet receiverWallet = buildWallet(receiver, BigDecimal.ZERO);

        when(valueOps.get(anyString())).thenReturn(null);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(sender));
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(receiver));
        mockWalletLockOrder(sender, senderWallet, receiver, receiverWallet);

        walletService.transfer("alice", UUID.randomUUID().toString(),
                new TransferRequest("bob", new BigDecimal("100000"), null));

        // Expected order derived the same way WalletService.transfer() computes it —
        // this verifies the two locks always happen in a fixed order for a given pair
        // of ids (the actual deadlock-prevention property), not a specific "smaller first"
        // assumption, since UUID.compareTo does a signed comparison of mostSigBits and
        // does not sort UUIDs the way their string form might suggest.
        UUID firstId = sender.getId().compareTo(receiver.getId()) < 0 ? sender.getId() : receiver.getId();
        UUID secondId = sender.getId().compareTo(receiver.getId()) < 0 ? receiver.getId() : sender.getId();

        var inOrder = org.mockito.Mockito.inOrder(walletRepository);
        inOrder.verify(walletRepository).findByUserIdWithLock(firstId);
        inOrder.verify(walletRepository).findByUserIdWithLock(secondId);
    }

    @Test
    void transfer_belowMinAmount_shouldThrowTransferAmountTooLow() {
        when(valueOps.get(anyString())).thenReturn(null);

        assertThatThrownBy(() -> walletService.transfer(
                "alice", UUID.randomUUID().toString(), new TransferRequest("bob", new BigDecimal("100"), null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.TRANSFER_AMOUNT_TOO_LOW));
    }

    @Test
    void transfer_aboveMaxAmount_shouldThrowTransferAmountTooHigh() {
        when(valueOps.get(anyString())).thenReturn(null);

        assertThatThrownBy(() -> walletService.transfer(
                "alice", UUID.randomUUID().toString(),
                new TransferRequest("bob", new BigDecimal("20000000"), null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.TRANSFER_AMOUNT_TOO_HIGH));
    }

    @Test
    void transfer_exceedingDailyLimit_shouldThrowDailyTransferLimitExceeded() {
        User sender = buildUser("alice");
        String dailyKey = "daily-transfer:" + sender.getId() + ":" + java.time.LocalDate.now(java.time.ZoneOffset.UTC);

        when(valueOps.get(anyString())).thenReturn(null);
        when(valueOps.get(dailyKey)).thenReturn("45000000");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(sender));

        assertThatThrownBy(() -> walletService.transfer(
                "alice", UUID.randomUUID().toString(),
                new TransferRequest("bob", new BigDecimal("9000000"), null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.DAILY_TRANSFER_LIMIT_EXCEEDED));
    }

    private void mockWalletLockOrder(User sender, Wallet senderWallet, User receiver, Wallet receiverWallet) {
        UUID firstId = sender.getId().compareTo(receiver.getId()) < 0 ? sender.getId() : receiver.getId();
        UUID secondId = sender.getId().compareTo(receiver.getId()) < 0 ? receiver.getId() : sender.getId();
        Wallet firstWallet = firstId.equals(sender.getId()) ? senderWallet : receiverWallet;
        Wallet secondWallet = firstId.equals(sender.getId()) ? receiverWallet : senderWallet;
        when(walletRepository.findByUserIdWithLock(firstId)).thenReturn(Optional.of(firstWallet));
        when(walletRepository.findByUserIdWithLock(secondId)).thenReturn(Optional.of(secondWallet));
    }

    private Transaction argThatTxn(java.util.function.Predicate<Transaction> predicate) {
        return org.mockito.ArgumentMatchers.argThat(predicate::test);
    }

    // ==================== withdraw() ====================

    @Test
    void withdraw_happyPath_shouldDebitBalanceAndComputeFee() {
        User user = buildUser("alice");
        Wallet wallet = buildWallet(user, new BigDecimal("1000000"));
        WithdrawRequest request = new WithdrawRequest(new BigDecimal("100000"), "1234567890", "Vietcombank");
        String idempotencyKey = UUID.randomUUID().toString();

        when(valueOps.get(anyString())).thenReturn(null);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(walletRepository.findByUserIdWithLock(user.getId())).thenReturn(Optional.of(wallet));

        WithdrawResponse response = walletService.withdraw("alice", idempotencyKey, request);

        assertThat(wallet.getBalance()).isEqualByComparingTo("900000");
        assertThat(response.fee()).isEqualByComparingTo("1000.0000");
        assertThat(response.netAmount()).isEqualByComparingTo("99000.0000");
    }

    @Test
    void withdraw_whenIdempotencyKeyCached_shouldReturnCachedResponse() {
        Transaction cached = Transaction.builder()
                .id(UUID.randomUUID())
                .amount(new BigDecimal("100000"))
                .fee(new BigDecimal("1000"))
                .status(TransactionStatus.COMPLETED)
                .build();
        String idempotencyKey = UUID.randomUUID().toString();
        WithdrawRequest request = new WithdrawRequest(new BigDecimal("100000"), "1234567890", "Vietcombank");

        when(valueOps.get("idempotency:" + idempotencyKey)).thenReturn(cached.getId().toString());
        when(transactionRepository.findById(cached.getId())).thenReturn(Optional.of(cached));

        WithdrawResponse response = walletService.withdraw("alice", idempotencyKey, request);

        assertThat(response.transactionId()).isEqualTo(cached.getId());
        verify(userRepository, org.mockito.Mockito.never()).findByUsername(anyString());
    }

    @Test
    void withdraw_whenWalletFrozen_shouldThrowWalletFrozen() {
        User user = buildUser("alice");
        Wallet wallet = buildWallet(user, new BigDecimal("1000000"));
        wallet.setFrozen(true);

        when(valueOps.get(anyString())).thenReturn(null);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(walletRepository.findByUserIdWithLock(user.getId())).thenReturn(Optional.of(wallet));

        assertThatThrownBy(() -> walletService.withdraw(
                "alice", UUID.randomUUID().toString(),
                new WithdrawRequest(new BigDecimal("100000"), "1234567890", "Vietcombank")))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.WALLET_FROZEN));
    }

    @Test
    void withdraw_whenInsufficientBalance_shouldThrowInsufficientBalance() {
        User user = buildUser("alice");
        Wallet wallet = buildWallet(user, new BigDecimal("1000"));

        when(valueOps.get(anyString())).thenReturn(null);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(walletRepository.findByUserIdWithLock(user.getId())).thenReturn(Optional.of(wallet));

        assertThatThrownBy(() -> walletService.withdraw(
                "alice", UUID.randomUUID().toString(),
                new WithdrawRequest(new BigDecimal("100000"), "1234567890", "Vietcombank")))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.INSUFFICIENT_BALANCE));
    }

    @Test
    void withdraw_feeCalculation_shouldBeExactOnePercent() {
        User user = buildUser("alice");
        Wallet wallet = buildWallet(user, new BigDecimal("1000000"));

        when(valueOps.get(anyString())).thenReturn(null);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(walletRepository.findByUserIdWithLock(user.getId())).thenReturn(Optional.of(wallet));

        WithdrawResponse response = walletService.withdraw("alice", UUID.randomUUID().toString(),
                new WithdrawRequest(new BigDecimal("200000"), "1234567890", "Vietcombank"));

        assertThat(response.fee()).isEqualByComparingTo(new BigDecimal("200000").multiply(new BigDecimal("0.01")));
        assertThat(response.netAmount()).isEqualByComparingTo(response.amount().subtract(response.fee()));
    }

    // ==================== freezeWallet() / unfreezeWallet() ====================

    @Test
    void freezeWallet_shouldSetFrozenTrue() {
        User user = buildUser("alice");
        Wallet wallet = buildWallet(user, BigDecimal.ZERO);
        UUID userId = user.getId();

        when(walletRepository.findByUserId(userId)).thenReturn(Optional.of(wallet));

        walletService.freezeWallet(userId);

        assertThat(wallet.isFrozen()).isTrue();
        verify(walletRepository).save(wallet);
    }

    @Test
    void freezeWallet_whenAlreadyFrozen_shouldThrowWalletAlreadyFrozen() {
        User user = buildUser("alice");
        Wallet wallet = buildWallet(user, BigDecimal.ZERO);
        wallet.setFrozen(true);
        UUID userId = user.getId();

        when(walletRepository.findByUserId(userId)).thenReturn(Optional.of(wallet));

        assertThatThrownBy(() -> walletService.freezeWallet(userId))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.WALLET_ALREADY_FROZEN));
    }

    @Test
    void unfreezeWallet_shouldSetFrozenFalse() {
        User user = buildUser("alice");
        Wallet wallet = buildWallet(user, BigDecimal.ZERO);
        wallet.setFrozen(true);
        UUID userId = user.getId();

        when(walletRepository.findByUserId(userId)).thenReturn(Optional.of(wallet));

        walletService.unfreezeWallet(userId);

        assertThat(wallet.isFrozen()).isFalse();
        verify(walletRepository).save(wallet);
    }

    @Test
    void unfreezeWallet_whenNotFrozen_shouldThrowWalletNotFrozen() {
        User user = buildUser("alice");
        Wallet wallet = buildWallet(user, BigDecimal.ZERO);
        UUID userId = user.getId();

        when(walletRepository.findByUserId(userId)).thenReturn(Optional.of(wallet));

        assertThatThrownBy(() -> walletService.unfreezeWallet(userId))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.WALLET_NOT_FROZEN));
    }
}
