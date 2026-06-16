package com.hoanghnt.swiftpay.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hoanghnt.swiftpay.dto.request.TransferRequest;
import com.hoanghnt.swiftpay.dto.request.WithdrawRequest;
import com.hoanghnt.swiftpay.dto.response.TransactionResponse;
import com.hoanghnt.swiftpay.dto.response.WalletResponse;
import com.hoanghnt.swiftpay.dto.response.WithdrawResponse;
import com.hoanghnt.swiftpay.entity.Transaction;
import com.hoanghnt.swiftpay.entity.TransactionStatus;
import com.hoanghnt.swiftpay.entity.TransactionType;
import com.hoanghnt.swiftpay.entity.User;
import com.hoanghnt.swiftpay.entity.Wallet;
import com.hoanghnt.swiftpay.exception.ErrorCode;
import com.hoanghnt.swiftpay.exception.custom.BusinessException;
import com.hoanghnt.swiftpay.exception.custom.ResourceNotFoundException;
import com.hoanghnt.swiftpay.repository.TransactionRepository;
import com.hoanghnt.swiftpay.repository.UserRepository;
import com.hoanghnt.swiftpay.repository.WalletRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class WalletService {

        private final WalletRepository walletRepository;
        private final UserRepository userRepository;
        private final TransactionRepository transactionRepository;
        private final RedisTemplate<String, String> redisTemplate;

        private static final String IDEMPOTENCY_PREFIX = "idempotency:";
        private static final long IDEMPOTENCY_TTL_HOURS = 24;

        @Value("${app.wallet.withdraw-fee-percent:0.01}")
        private BigDecimal withdrawFeePercent;

        @Transactional(readOnly = true)
        public WalletResponse getMyWallet(String username) {
                User user = userRepository.findByUsername(username)
                                .orElseThrow(() -> new ResourceNotFoundException("User", username));

                Wallet wallet = walletRepository.findByUserId(user.getId())
                                .orElseThrow(() -> new ResourceNotFoundException("Wallet", user.getId()));

                return new WalletResponse(
                                wallet.getId(),
                                wallet.getBalance(),
                                wallet.getCurrency(),
                                wallet.isFrozen(),
                                wallet.getCreatedAt());
        }

        @Transactional
        public TransactionResponse transfer(String senderUsername, String idempotencyKey, TransferRequest request) {

                // 1. Idempotency check
                String redisKey = IDEMPOTENCY_PREFIX + idempotencyKey;
                String cachedTransactionId = redisTemplate.opsForValue().get(redisKey);
                if (cachedTransactionId != null) {
                        return transactionRepository.findById(UUID.fromString(cachedTransactionId))
                                        .map(this::toResponse)
                                        .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));
                }

                // 2. Load sender
                User sender = userRepository.findByUsername(senderUsername)
                                .orElseThrow(() -> new ResourceNotFoundException("User", senderUsername));

                // 3. Check self-transfer
                if (sender.getUsername().equals(request.receiverUsername())) {
                        throw new BusinessException(ErrorCode.SELF_TRANSFER_NOT_ALLOWED);
                }

                // 4. Load receiver
                User receiver = userRepository.findByUsername(request.receiverUsername())
                                .orElseThrow(() -> new ResourceNotFoundException("User", request.receiverUsername()));

                // 5. Load wallets WITH LOCK
                UUID firstId = sender.getId().compareTo(receiver.getId()) < 0 ? sender.getId() : receiver.getId();
                UUID secondId = sender.getId().compareTo(receiver.getId()) < 0 ? receiver.getId() : sender.getId();

                Wallet firstWallet = walletRepository.findByUserIdWithLock(firstId)
                                .orElseThrow(() -> new BusinessException(ErrorCode.WALLET_NOT_FOUND));
                Wallet secondWallet = walletRepository.findByUserIdWithLock(secondId)
                                .orElseThrow(() -> new BusinessException(ErrorCode.WALLET_NOT_FOUND));

                Wallet senderWallet = firstId.equals(sender.getId()) ? firstWallet : secondWallet;
                Wallet receiverWallet = firstId.equals(sender.getId()) ? secondWallet : firstWallet;

                // 6. Validate
                if (senderWallet.isFrozen()) {
                        throw new BusinessException(ErrorCode.WALLET_FROZEN);
                }
                if (receiverWallet.isFrozen()) {
                        throw new BusinessException(ErrorCode.WALLET_FROZEN);
                }
                if (senderWallet.getBalance().compareTo(request.amount()) < 0) {
                        throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE);
                }

                // 7. Debit sender
                senderWallet.setBalance(senderWallet.getBalance().subtract(request.amount()));
                walletRepository.save(senderWallet);

                // 8. Credit receiver
                receiverWallet.setBalance(receiverWallet.getBalance().add(request.amount()));
                walletRepository.save(receiverWallet);

                // 9. Create transaction record
                Transaction transaction = Transaction.builder()
                                .idempotencyKey(UUID.fromString(idempotencyKey))
                                .sender(sender)
                                .receiver(receiver)
                                .amount(request.amount())
                                .type(TransactionType.TRANSFER)
                                .status(TransactionStatus.COMPLETED)
                                .description(request.description())
                                .build();
                transaction = transactionRepository.save(transaction);

                // 10. Store idempotency key in Redis
                redisTemplate.opsForValue().set(
                                redisKey,
                                transaction.getId().toString(),
                                Duration.ofHours(IDEMPOTENCY_TTL_HOURS));

                log.info("Transfer completed: {} → {} | amount={} | txnId={}",
                                senderUsername, request.receiverUsername(), request.amount(), transaction.getId());

                return toResponse(transaction);
        }

        private TransactionResponse toResponse(Transaction txn) {
                return new TransactionResponse(
                                txn.getId(),
                                txn.getType().name(),
                                txn.getStatus().name(),
                                txn.getAmount(),
                                txn.getFee(),
                                txn.getSender() != null ? txn.getSender().getUsername() : null,
                                txn.getReceiver() != null ? txn.getReceiver().getUsername() : null,
                                txn.getDescription(),
                                txn.getCreatedAt());
        }

        @Transactional
        public WithdrawResponse withdraw(String username, WithdrawRequest request) {

                User user = userRepository.findByUsername(username)
                                .orElseThrow(() -> new ResourceNotFoundException("User", username));

                Wallet wallet = walletRepository.findByUserIdWithLock(user.getId())
                                .orElseThrow(() -> new BusinessException(ErrorCode.WALLET_NOT_FOUND));

                if (wallet.isFrozen()) {
                        throw new BusinessException(ErrorCode.WALLET_FROZEN);
                }

                BigDecimal fee = request.amount()
                                .multiply(withdrawFeePercent)
                                .setScale(4, RoundingMode.HALF_UP);
                BigDecimal netAmount = request.amount().subtract(fee);

                if (wallet.getBalance().compareTo(request.amount()) < 0) {
                        throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE);
                }

                // Debit
                wallet.setBalance(wallet.getBalance().subtract(request.amount()));
                walletRepository.save(wallet);

                Transaction transaction = Transaction.builder()
                                .idempotencyKey(UUID.randomUUID())
                                .sender(user)
                                .receiver(null)
                                .amount(request.amount())
                                .fee(fee)
                                .type(TransactionType.WITHDRAW)
                                .status(TransactionStatus.COMPLETED)
                                .description("Withdraw to " + request.bankName() + " - " + request.bankAccountNumber())
                                .build();
                transaction = transactionRepository.save(transaction);

                log.info("Withdraw completed: user={} | amount={} | fee={} | txnId={}",
                                username, request.amount(), fee, transaction.getId());

                return new WithdrawResponse(
                                transaction.getId(),
                                request.amount(),
                                fee,
                                netAmount,
                                transaction.getStatus().name(),
                                request.bankAccountNumber(),
                                request.bankName(),
                                transaction.getCreatedAt());
        }

        @Transactional
        public void freezeWallet(UUID userId) {
                Wallet wallet = walletRepository.findByUserId(userId)
                                .orElseThrow(() -> new BusinessException(ErrorCode.WALLET_NOT_FOUND));

                if (wallet.isFrozen()) {
                        throw new BusinessException(ErrorCode.WALLET_ALREADY_FROZEN);
                }

                wallet.setFrozen(true);
                walletRepository.save(wallet);
                log.info("Wallet frozen: userId={}", userId);
        }

        @Transactional
        public void unfreezeWallet(UUID userId) {
                Wallet wallet = walletRepository.findByUserId(userId)
                                .orElseThrow(() -> new BusinessException(ErrorCode.WALLET_NOT_FOUND));

                if (!wallet.isFrozen()) {
                        throw new BusinessException(ErrorCode.WALLET_NOT_FROZEN);
                }

                wallet.setFrozen(false);
                walletRepository.save(wallet);
                log.info("Wallet unfrozen: userId={}", userId);
        }
}
