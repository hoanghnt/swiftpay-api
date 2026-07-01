package com.hoanghnt.swiftpay.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.hoanghnt.swiftpay.entity.Transaction;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID>, JpaSpecificationExecutor<Transaction>  {
    Optional<Transaction> findByIdempotencyKey(UUID idempotencyKey);
    Optional<Transaction> findByVnpTxnRef(String vnpTxnRef);
    boolean existsByVnpTxnRef(String vnpTxnRef);

    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.createdAt >= :since")
    long countSince(@Param("since") LocalDateTime since);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.type = 'TRANSFER' AND t.createdAt >= :since")
    BigDecimal sumTransferAmountSince(@Param("since") LocalDateTime since);
}
