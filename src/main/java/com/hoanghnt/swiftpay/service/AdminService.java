package com.hoanghnt.swiftpay.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;

import org.springframework.stereotype.Service;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;

import com.hoanghnt.swiftpay.client.DownstreamUnavailableException;
import com.hoanghnt.swiftpay.client.TransactionAdminClient;
import com.hoanghnt.swiftpay.client.UserAdminClient;
import com.hoanghnt.swiftpay.client.WalletAdminClient;
import com.hoanghnt.swiftpay.client.dto.TxnSummaryView;
import com.hoanghnt.swiftpay.client.dto.UserSummaryView;
import com.hoanghnt.swiftpay.client.dto.UserView;
import com.hoanghnt.swiftpay.client.dto.WalletSummaryView;
import com.hoanghnt.swiftpay.client.dto.WalletView;
import com.hoanghnt.swiftpay.dto.response.AdminSummaryResponse;
import com.hoanghnt.swiftpay.dto.response.AdminUserDetailResponse;
import com.hoanghnt.swiftpay.dto.response.AdminUserResponse;
import com.hoanghnt.swiftpay.dto.response.PageResponse;
import com.hoanghnt.swiftpay.dto.response.TransactionResponse;
import com.hoanghnt.swiftpay.entity.TransactionStatus;
import com.hoanghnt.swiftpay.entity.TransactionType;
import com.hoanghnt.swiftpay.exception.ErrorCode;
import com.hoanghnt.swiftpay.exception.custom.BusinessException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminService {

    private final UserAdminClient userAdminClient;
    private final WalletAdminClient walletAdminClient;
    private final TransactionAdminClient transactionAdminClient;
    private final ExecutorService fanoutExecutor;

    public PageResponse<AdminUserResponse> listUsers(String search, int page, int size) {
        PageResponse<UserView> src = userAdminClient.listUsers(search, page, size);
        List<AdminUserResponse> content = src.content().stream().map(this::toAdminUserResponse).toList();
        return new PageResponse<>(content, src.page(), src.size(),
                src.totalElements(), src.totalPages(), src.last());
    }

    public AdminUserDetailResponse getUserDetail(UUID userId) {

        var uF = CompletableFuture.supplyAsync(() -> userAdminClient.getUser(userId), fanoutExecutor);
        var wF = CompletableFuture.supplyAsync(() -> walletAdminClient.getWallet(userId), fanoutExecutor);

        List<String> unavailable = new ArrayList<>();
        Optional<UserView> uOpt = joinOrNull(uF, "auth-service", unavailable);
        if (uOpt == null) {
            throw new DownstreamUnavailableException("auth-service",
                    "Không lấy được thông tin người dùng vì auth-service không khả dụng", null);
        }
        UserView u = uOpt.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Optional<WalletView> wOpt = joinOrNull(wF, "wallet-service", unavailable);
        WalletView w = wOpt == null ? null : wOpt.orElse(null);

        return new AdminUserDetailResponse(
                u.id(), u.username(), u.email(), u.phone(), u.fullName(), u.role(),
                u.emailVerified(), u.enabled(), u.failedLoginAttempts(), u.lockedUntil(), u.lastLoginAt(),
                w != null ? w.id() : null,
                w != null ? w.balance() : null,
                w != null ? w.currency() : null,
                w != null && w.frozen(),
                !unavailable.isEmpty(),
                List.copyOf(unavailable));
    }

    public void enableUser(UUID userId) {
        userAdminClient.enable(userId);
        log.info("Admin enabled user via auth-service: userId={}", userId);
    }

    public void disableUser(UUID userId) {
        userAdminClient.disable(userId);
        log.info("Admin disabled user via auth-service: userId={}", userId);
    }

    public void unlockUser(UUID userId) {
        userAdminClient.unlock(userId);
        log.info("Admin unlocked user via auth-service: userId={}", userId);
    }

    public void freezeWallet(UUID userId) {
        walletAdminClient.freeze(userId);
        log.info("Admin froze wallet via wallet-service: userId={}", userId);
    }

    public void unfreezeWallet(UUID userId) {
        walletAdminClient.unfreeze(userId);
        log.info("Admin unfroze wallet via wallet-service: userId={}", userId);
    }

    public PageResponse<TransactionResponse> listAllTransactions(
            TransactionType type, TransactionStatus status,
            LocalDateTime from, LocalDateTime to, int page, int size) {
        return transactionAdminClient.listAll(type, status, from, to, page, size);
    }

    public TransactionResponse getTransactionById(UUID transactionId) {
        return transactionAdminClient.getById(transactionId);
    }

    public AdminSummaryResponse getSummary() {
        var usF = CompletableFuture.supplyAsync(userAdminClient::summary, fanoutExecutor);
        var wsF = CompletableFuture.supplyAsync(walletAdminClient::summary, fanoutExecutor);
        var tsF = CompletableFuture.supplyAsync(transactionAdminClient::summary, fanoutExecutor);

        List<String> unavailable = new ArrayList<>();
        UserSummaryView us = joinOrNull(usF, "auth-service", unavailable);
        WalletSummaryView ws = joinOrNull(wsF, "wallet-service", unavailable);
        TxnSummaryView ts = joinOrNull(tsF, "transaction-service", unavailable);

        return new AdminSummaryResponse(
                us != null ? us.totalUsers() : 0,
                us != null ? us.activeUsers() : 0,
                us != null ? us.lockedUsers() : 0,
                ws != null ? ws.totalWallets() : 0,
                ws != null ? ws.frozenWallets() : 0,
                ws != null ? ws.totalBalance() : BigDecimal.ZERO,
                ts != null ? ts.transactionsToday() : 0,
                ts != null ? ts.transferVolumeToday() : BigDecimal.ZERO,
                !unavailable.isEmpty(),
                List.copyOf(unavailable));
    }

    private static <T> T joinOrNull(CompletableFuture<T> future, String service, List<String> unavailable) {
        try {
            return join(future);
        } catch (DownstreamUnavailableException | CallNotPermittedException e) {
            log.warn("Admin summary: {} không khả dụng — trả dữ liệu một phần", service);
            unavailable.add(service);
            return null;
        }
    }

    private static <T> T join(CompletableFuture<T> future) {
        try {
            return future.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException re) {
                throw re;
            }
            throw e;
        }
    }

    private AdminUserResponse toAdminUserResponse(UserView u) {
        return new AdminUserResponse(
                u.id(), u.username(), u.email(), u.phone(), u.fullName(), u.role(),
                u.emailVerified(), u.enabled(), u.locked(), u.lastLoginAt(), u.createdAt());
    }
}
