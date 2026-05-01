package com.hoanghnt.swiftpay.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.hoanghnt.swiftpay.entity.Transaction;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    Optional<Transaction> findByIdempotencyKey(UUID idempotencyKey);
    Optional<Transaction> findByVnpTxnRef(String vnpTxnRef);
}
