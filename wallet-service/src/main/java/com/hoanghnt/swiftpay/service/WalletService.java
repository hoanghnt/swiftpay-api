package com.hoanghnt.swiftpay.service;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hoanghnt.swiftpay.audit.AuditEventType;
import com.hoanghnt.swiftpay.audit.AuditService;
import com.hoanghnt.swiftpay.dto.request.InternalCreditRequest;
import com.hoanghnt.swiftpay.dto.request.InternalDebitRequest;
import com.hoanghnt.swiftpay.dto.request.InternalTransferRequest;
import com.hoanghnt.swiftpay.dto.response.WalletOperationResponse;
import com.hoanghnt.swiftpay.dto.response.WalletResponse;
import com.hoanghnt.swiftpay.entity.Wallet;
import com.hoanghnt.swiftpay.entity.WalletOperation;
import com.hoanghnt.swiftpay.entity.WalletOperationType;
import com.hoanghnt.swiftpay.exception.ErrorCode;
import com.hoanghnt.swiftpay.exception.custom.BusinessException;
import com.hoanghnt.swiftpay.exception.custom.ResourceNotFoundException;
import com.hoanghnt.swiftpay.repository.WalletOperationRepository;
import com.hoanghnt.swiftpay.repository.WalletRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class WalletService {

    private final WalletRepository walletRepository;
    private final WalletOperationRepository walletOperationRepository;
    private final AuditService auditService;

    @Transactional
    public WalletResponse getMyWallet(UUID userId) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseGet(() -> createWalletIfMissing(userId));

        return toWalletResponse(wallet);
    }

    private Wallet createWalletIfMissing(UUID userId) {
        try {
            return walletRepository.save(Wallet.builder().userId(userId).build());
        } catch (DataIntegrityViolationException e) {
            return walletRepository.findByUserId(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("Wallet", userId));
        }
    }

    @Transactional(readOnly = true)
    public java.util.Optional<WalletResponse> getWalletView(UUID userId) {
        return walletRepository.findByUserId(userId).map(this::toWalletResponse);
    }

    @Transactional(readOnly = true)
    public com.hoanghnt.swiftpay.dto.response.WalletSummaryResponse getWalletSummary() {
        return new com.hoanghnt.swiftpay.dto.response.WalletSummaryResponse(
                walletRepository.count(),
                walletRepository.countByFrozenTrue(),
                walletRepository.sumAllBalances());
    }

    @Transactional
    public WalletOperationResponse transfer(InternalTransferRequest req) {
        if (req.fromUserId().equals(req.toUserId())) {
            throw new BusinessException(ErrorCode.SELF_TRANSFER_NOT_ALLOWED);
        }

        // 1. Lock both wallets in ascending UUID order (avoid deadlock) — BEFORE the idempotency check.
        UUID firstId = req.fromUserId().compareTo(req.toUserId()) < 0 ? req.fromUserId() : req.toUserId();
        UUID secondId = req.fromUserId().compareTo(req.toUserId()) < 0 ? req.toUserId() : req.fromUserId();

        Wallet firstWallet = walletRepository.findByUserIdWithLock(firstId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WALLET_NOT_FOUND));
        Wallet secondWallet = walletRepository.findByUserIdWithLock(secondId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WALLET_NOT_FOUND));

        // 2. Wallet-level idempotency UNDER lock — if op_key already applied, return it, do NOT re-apply.
        var existing = walletOperationRepository.findByOpKey(req.opKey());
        if (existing.isPresent()) {
            log.info("Idempotent replay of transfer op_key={} — not re-applied", req.opKey());
            return toReplay(existing.get());
        }

        Wallet senderWallet = firstId.equals(req.fromUserId()) ? firstWallet : secondWallet;
        Wallet receiverWallet = firstId.equals(req.fromUserId()) ? secondWallet : firstWallet;

        // 3. Validate
        if (senderWallet.isFrozen() || receiverWallet.isFrozen()) {
            throw new BusinessException(ErrorCode.WALLET_FROZEN);
        }
        if (senderWallet.getBalance().compareTo(req.amount()) < 0) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE);
        }

        // 4. Debit + credit
        senderWallet.setBalance(senderWallet.getBalance().subtract(req.amount()));
        receiverWallet.setBalance(receiverWallet.getBalance().add(req.amount()));
        walletRepository.save(senderWallet);
        walletRepository.save(receiverWallet);

        // 5. Record op_key (idempotency + audit trail for reconciliation)
        WalletOperation op = recordOperation(req.opKey(), WalletOperationType.TRANSFER,
                req.fromUserId(), req.toUserId(), req.amount());

        log.info("Transfer applied: {} → {} | amount={} | op_key={}",
                req.fromUserId(), req.toUserId(), req.amount(), req.opKey());
        return toApplied(op);
    }

    @Transactional
    public WalletOperationResponse credit(InternalCreditRequest req) {
        // 1. Lock wallet
        Wallet wallet = walletRepository.findByUserIdWithLock(req.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.WALLET_NOT_FOUND));

        // 2. Idempotency under lock — if op_key already applied, return it, do NOT re-apply
        var existing = walletOperationRepository.findByOpKey(req.opKey());
        if (existing.isPresent()) {
            log.info("Idempotent replay of credit op_key={} — not re-applied", req.opKey());
            return toReplay(existing.get());
        }

        // 3. Credit balance
        wallet.setBalance(wallet.getBalance().add(req.amount()));
        walletRepository.save(wallet);

        // 4. Record op_key (idempotency + audit trail)
        WalletOperation op = recordOperation(req.opKey(), WalletOperationType.CREDIT,
                null, req.userId(), req.amount());

        log.info("Credit applied: userId={} | amount={} | op_key={}", req.userId(), req.amount(), req.opKey());
        return toApplied(op);
    }

    @Transactional
    public WalletOperationResponse debit(InternalDebitRequest req) {
        // 1. Lock wallet
        Wallet wallet = walletRepository.findByUserIdWithLock(req.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.WALLET_NOT_FOUND));

        // 2. Idempotency under lock — if op_key already applied, return it, do NOT re-apply
        var existing = walletOperationRepository.findByOpKey(req.opKey());
        if (existing.isPresent()) {
            log.info("Idempotent replay of debit op_key={} — not re-applied", req.opKey());
            return toReplay(existing.get());
        }

        // 3. Validate (frozen + sufficient balance)
        if (wallet.isFrozen()) {
            throw new BusinessException(ErrorCode.WALLET_FROZEN);
        }
        if (wallet.getBalance().compareTo(req.amount()) < 0) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE);
        }

        // 4. Debit balance
        wallet.setBalance(wallet.getBalance().subtract(req.amount()));
        walletRepository.save(wallet);

        // 5. Record op_key (idempotency + audit trail)
        WalletOperation op = recordOperation(req.opKey(), WalletOperationType.DEBIT,
                req.userId(), null, req.amount());

        log.info("Debit applied: userId={} | amount={} | op_key={}", req.userId(), req.amount(), req.opKey());
        return toApplied(op);
    }

    private WalletOperation recordOperation(String opKey, WalletOperationType type,
            UUID fromUserId, UUID toUserId, BigDecimal amount) {
        return walletOperationRepository.save(WalletOperation.builder()
                .opKey(opKey)
                .opType(type)
                .fromUserId(fromUserId)
                .toUserId(toUserId)
                .amount(amount)
                .build());
    }

    @Transactional(readOnly = true)
    public boolean isOperationApplied(String opKey) {
        return walletOperationRepository.existsByOpKey(opKey);
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
        auditService.logSuccess(AuditEventType.WALLET_FROZEN, null, userId);
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
        auditService.logSuccess(AuditEventType.WALLET_UNFROZEN, null, userId);
    }

    private WalletResponse toWalletResponse(Wallet wallet) {
        return new WalletResponse(
                wallet.getId(),
                wallet.getBalance(),
                wallet.getCurrency(),
                wallet.isFrozen(),
                wallet.getCreatedAt());
    }

    private WalletOperationResponse toApplied(WalletOperation op) {
        return new WalletOperationResponse(op.getOpKey(), op.getOpType().name(), op.getAmount(),
                true, false, op.getCreatedAt());
    }

    private WalletOperationResponse toReplay(WalletOperation op) {
        return new WalletOperationResponse(op.getOpKey(), op.getOpType().name(), op.getAmount(),
                true, true, op.getCreatedAt());
    }
}
