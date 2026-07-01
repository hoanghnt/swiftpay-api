package com.hoanghnt.swiftpay.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hoanghnt.swiftpay.dto.response.AdminSummaryResponse;
import com.hoanghnt.swiftpay.dto.response.AdminUserDetailResponse;
import com.hoanghnt.swiftpay.dto.response.AdminUserResponse;
import com.hoanghnt.swiftpay.dto.response.TransactionResponse;
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
import com.hoanghnt.swiftpay.repository.specification.TransactionSpecification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminService {

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;

    @Transactional(readOnly = true)
    public Page<AdminUserResponse> listUsers(String search, Pageable pageable) {
        Page<User> users = (search == null || search.isBlank())
                ? userRepository.findAll(pageable)
                : userRepository.findByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                        search, search, pageable);
        return users.map(this::toAdminUserResponse);
    }

    @Transactional(readOnly = true)
    public AdminUserDetailResponse getUserDetail(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Wallet wallet = walletRepository.findByUserId(userId).orElse(null);

        return new AdminUserDetailResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getPhone(),
                user.getFullName(),
                user.getRole().name(),
                user.isEmailVerified(),
                user.isEnabled(),
                user.getFailedLoginAttempts(),
                user.getLockedUntil(),
                user.getLastLoginAt(),
                wallet != null ? wallet.getId() : null,
                wallet != null ? wallet.getBalance() : null,
                wallet != null ? wallet.getCurrency() : null,
                wallet != null && wallet.isFrozen());
    }

    @Transactional
    public void enableUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (user.isEnabled()) {
            throw new BusinessException(ErrorCode.USER_ALREADY_ENABLED);
        }
        user.setEnabled(true);
        userRepository.save(user);
        log.info("Admin enabled user: userId={}", userId);
    }

    @Transactional
    public void disableUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (!user.isEnabled()) {
            throw new BusinessException(ErrorCode.USER_ALREADY_DISABLED);
        }
        user.setEnabled(false);
        userRepository.save(user);
        log.info("Admin disabled user: userId={}", userId);
    }

    @Transactional
    public void unlockUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);
        log.info("Admin unlocked user: userId={}", userId);
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> listAllTransactions(
            TransactionType type, TransactionStatus status,
            LocalDateTime from, LocalDateTime to, Pageable pageable) {

        Specification<Transaction> spec = Specification.allOf(
                TransactionSpecification.hasType(type),
                TransactionSpecification.hasStatus(status),
                TransactionSpecification.createdAfter(from),
                TransactionSpecification.createdBefore(to));

        return transactionRepository.findAll(spec, pageable).map(this::toTransactionResponse);
    }

    @Transactional(readOnly = true)
    public TransactionResponse getTransactionById(UUID transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        return toTransactionResponse(transaction);
    }

    @Transactional(readOnly = true)
    public AdminSummaryResponse getSummary() {
        LocalDateTime startOfToday = LocalDate.now().atStartOfDay();

        return new AdminSummaryResponse(
                userRepository.count(),
                userRepository.countByEnabledTrue(),
                userRepository.countByLockedUntilAfter(LocalDateTime.now()),
                walletRepository.count(),
                walletRepository.countByFrozenTrue(),
                walletRepository.sumAllBalances(),
                transactionRepository.countSince(startOfToday),
                transactionRepository.sumTransferAmountSince(startOfToday));
    }

    private AdminUserResponse toAdminUserResponse(User user) {
        return new AdminUserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getPhone(),
                user.getFullName(),
                user.getRole().name(),
                user.isEmailVerified(),
                user.isEnabled(),
                user.isLocked(),
                user.getLastLoginAt(),
                user.getCreatedAt());
    }

    private TransactionResponse toTransactionResponse(Transaction txn) {
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
