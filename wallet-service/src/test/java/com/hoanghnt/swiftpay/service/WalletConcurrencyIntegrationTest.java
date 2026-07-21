package com.hoanghnt.swiftpay.service;

import com.hoanghnt.swiftpay.audit.AuditService;
import com.hoanghnt.swiftpay.config.JpaAuditingConfig;
import com.hoanghnt.swiftpay.dto.request.InternalTransferRequest;
import com.hoanghnt.swiftpay.entity.Wallet;
import com.hoanghnt.swiftpay.exception.custom.BusinessException;
import com.hoanghnt.swiftpay.repository.WalletOperationRepository;
import com.hoanghnt.swiftpay.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({WalletService.class, JpaAuditingConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class WalletConcurrencyIntegrationTest {

    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static final Path SHARED_SCHEMA =
            Path.of("..", "docker", "postgres", "init", "01-init-service-databases.sql");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        POSTGRES.start();
        applyWalletSchema();

        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "30");
    }

    private static void applyWalletSchema() {
        String sql;
        try {
            sql = Files.readString(SHARED_SCHEMA);
        } catch (IOException e) {
            throw new IllegalStateException("Không đọc được schema dùng chung: " + SHARED_SCHEMA.toAbsolutePath(), e);
        }

        int start = sql.indexOf("\\connect wallet_db");
        int end = sql.indexOf("\\connect txn_db");
        if (start < 0 || end < 0 || end <= start) {
            throw new IllegalStateException(
                    "Không tìm thấy đoạn wallet_db trong " + SHARED_SCHEMA + " — file init đã đổi cấu trúc?");
        }
        String walletDdl = sql.substring(start + "\\connect wallet_db".length(), end);

        try (Connection conn = java.sql.DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                Statement st = conn.createStatement()) {
            st.execute(walletDdl);
        } catch (SQLException e) {
            throw new IllegalStateException("Áp schema wallet_db thất bại", e);
        }
    }

    @Autowired
    private WalletService walletService;
    @Autowired
    private WalletRepository walletRepository;
    @Autowired
    private WalletOperationRepository walletOperationRepository;
    @Autowired
    private DataSource dataSource;

    @MockitoBean
    private AuditService auditService;

    @BeforeEach
    void clean() {
        walletOperationRepository.deleteAll();
        walletRepository.deleteAll();
    }

    private UUID newWallet(String balance) {
        UUID userId = UUID.randomUUID();
        walletRepository.saveAndFlush(Wallet.builder()
                .userId(userId)
                .balance(new BigDecimal(balance))
                .build());
        return userId;
    }

    private BigDecimal balanceOf(UUID userId) {
        return walletRepository.findByUserId(userId).orElseThrow().getBalance();
    }

    private <T> List<Outcome<T>> runConcurrently(int n, Callable<T> task) throws Exception {
        CountDownLatch ready = new CountDownLatch(n);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Outcome<T>>> futures = new ArrayList<>();

        try (ExecutorService pool = Executors.newFixedThreadPool(n)) {
            for (int i = 0; i < n; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    try {
                        return new Outcome<>(task.call(), null);
                    } catch (Exception e) {
                        return new Outcome<T>(null, e);
                    }
                }));
            }
            assertThat(ready.await(30, TimeUnit.SECONDS)).as("mọi thread đã sẵn sàng").isTrue();
            go.countDown();

            List<Outcome<T>> results = new ArrayList<>();
            for (Future<Outcome<T>> f : futures) {
                results.add(f.get(60, TimeUnit.SECONDS));
            }
            return results;
        }
    }

    private record Outcome<T>(T value, Exception error) {
        boolean ok() {
            return error == null;
        }
    }

    @Test
    @DisplayName("20 transfer đồng thời qua lại giữa 4 ví — TỔNG TIỀN không đổi, không ví nào âm")
    void concurrentTransfersConserveTotalMoney() throws Exception {
        List<UUID> users = List.of(newWallet("1000"), newWallet("1000"), newWallet("1000"), newWallet("1000"));
        BigDecimal totalBefore = users.stream().map(this::balanceOf)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        AtomicInteger seq = new AtomicInteger();
        List<Outcome<Object>> results = runConcurrently(20, () -> {
            int i = seq.getAndIncrement();
            UUID from = users.get(i % users.size());
            UUID to = users.get((i + 1) % users.size());
            return walletService.transfer(new InternalTransferRequest(
                    from, to, new BigDecimal("50.0000"), UUID.randomUUID().toString()));
        });

        BigDecimal totalAfter = users.stream().map(this::balanceOf)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(totalAfter).isEqualByComparingTo(totalBefore);
        assertThat(users).allSatisfy(u -> assertThat(balanceOf(u)).isGreaterThanOrEqualTo(BigDecimal.ZERO));
        assertThat(results).allSatisfy(r ->
                assertThat(r.ok() || r.error() instanceof BusinessException)
                        .as("lỗi bất ngờ: %s", r.error()).isTrue());
    }

    @Test
    @DisplayName("10 request ĐỒNG THỜI cùng op_key — tiền chỉ chuyển ĐÚNG MỘT lần")
    void sameOpKeyConcurrentlyAppliesOnce() throws Exception {
        UUID from = newWallet("1000");
        UUID to = newWallet("0");
        String opKey = UUID.randomUUID().toString();

        runConcurrently(10, () -> walletService.transfer(
                new InternalTransferRequest(from, to, new BigDecimal("100.0000"), opKey)));

        assertThat(balanceOf(from)).isEqualByComparingTo("900");
        assertThat(balanceOf(to)).isEqualByComparingTo("100");
        assertThat(walletOperationRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Chi vượt số dư dưới đồng thời: số dư 100, 10 lệnh 100 cùng lúc → đúng 1 lệnh qua, không âm")
    void concurrentOverdraftAllowsExactlyOne() throws Exception {
        UUID from = newWallet("100");
        UUID to = newWallet("0");

        List<Outcome<Object>> results = runConcurrently(10, () -> walletService.transfer(
                new InternalTransferRequest(from, to, new BigDecimal("100.0000"), UUID.randomUUID().toString())));

        long succeeded = results.stream().filter(Outcome::ok).count();
        assertThat(succeeded).isEqualTo(1);
        assertThat(balanceOf(from)).isEqualByComparingTo("0");
        assertThat(balanceOf(to)).isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("A→B và B→A cùng lúc, lặp nhiều vòng — KHÔNG deadlock (bằng chứng cho thứ tự khóa UUID tăng dần)")
    void oppositeDirectionTransfersDoNotDeadlock() throws Exception {
        UUID a = newWallet("1000");
        UUID b = newWallet("1000");

        AtomicInteger seq = new AtomicInteger();
        List<Outcome<Object>> results = runConcurrently(16, () -> {
            boolean forward = seq.getAndIncrement() % 2 == 0;
            return walletService.transfer(new InternalTransferRequest(
                    forward ? a : b, forward ? b : a,
                    new BigDecimal("10.0000"), UUID.randomUUID().toString()));
        });

        assertThat(results).allSatisfy(r -> assertThat(r.ok()).as("lỗi: %s", r.error()).isTrue());
        assertThat(balanceOf(a).add(balanceOf(b))).isEqualByComparingTo("2000");
    }

    @Test
    @DisplayName("DECIMAL(19,4): số tiền lẻ đi qua DB thật vẫn CHÍNH XÁC tuyệt đối, không mất phần thập phân")
    void moneyKeepsExactScaleThroughDatabase() {
        UUID from = newWallet("10.5555");
        UUID to = newWallet("0");

        walletService.transfer(new InternalTransferRequest(
                from, to, new BigDecimal("0.1234"), UUID.randomUUID().toString()));

        assertThat(balanceOf(from)).isEqualByComparingTo("10.4321");
        assertThat(balanceOf(to)).isEqualByComparingTo("0.1234");
        assertThat(balanceOf(to).scale()).isEqualTo(4);
    }

    @Test
    @DisplayName("Ràng buộc DB là chốt chặn cuối: balance âm bị CHECK constraint từ chối")
    void databaseRejectsNegativeBalance() throws SQLException {
        UUID user = newWallet("100");

        try (Connection conn = dataSource.getConnection(); Statement st = conn.createStatement()) {
            assertThat(org.assertj.core.api.Assertions.catchThrowable(() ->
                    st.executeUpdate("UPDATE wallets SET balance = -1 WHERE user_id = '" + user + "'")))
                    .isInstanceOf(SQLException.class);
        }

        assertThat(balanceOf(user)).isEqualByComparingTo("100");
    }
}
