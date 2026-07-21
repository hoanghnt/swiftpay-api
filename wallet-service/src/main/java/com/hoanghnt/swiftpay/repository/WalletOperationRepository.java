package com.hoanghnt.swiftpay.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.hoanghnt.swiftpay.entity.WalletOperation;

@Repository
public interface WalletOperationRepository extends JpaRepository<WalletOperation, UUID> {

    Optional<WalletOperation> findByOpKey(String opKey);

    boolean existsByOpKey(String opKey);
}
