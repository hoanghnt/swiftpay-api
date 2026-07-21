package com.hoanghnt.swiftpay.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.hoanghnt.swiftpay.entity.UserRef;

@Repository
public interface UserRefRepository extends JpaRepository<UserRef, UUID> {

    Optional<UserRef> findByUsername(String username);
}
