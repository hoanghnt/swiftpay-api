package com.hoanghnt.swiftpay.service;

import java.time.Duration;
import java.util.UUID;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hoanghnt.swiftpay.dto.request.TransferRequest;
import com.hoanghnt.swiftpay.dto.response.TransactionResponse;
import com.hoanghnt.swiftpay.dto.response.WalletResponse;
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
}
