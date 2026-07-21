package com.hoanghnt.swiftpay.service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hoanghnt.swiftpay.dto.response.InternalUserSummary;
import com.hoanghnt.swiftpay.dto.response.InternalUserView;
import com.hoanghnt.swiftpay.entity.User;
import com.hoanghnt.swiftpay.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public Page<InternalUserView> list(String search, Pageable pageable) {
        Page<User> users = (search == null || search.isBlank())
                ? userRepository.findAll(pageable)
                : userRepository.findByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                        search, search, pageable);
        return users.map(this::toView);
    }

    @Transactional(readOnly = true)
    public Optional<InternalUserView> getById(UUID id) {
        return userRepository.findById(id).map(this::toView);
    }

    @Transactional
    public boolean enable(UUID id) {
        return userRepository.findById(id).map(u -> {
            u.setEnabled(true);
            userRepository.save(u);
            return true;
        }).orElse(false);
    }

    @Transactional
    public boolean disable(UUID id) {
        return userRepository.findById(id).map(u -> {
            u.setEnabled(false);
            userRepository.save(u);
            return true;
        }).orElse(false);
    }

    @Transactional
    public boolean unlock(UUID id) {
        return userRepository.findById(id).map(u -> {
            u.setFailedLoginAttempts(0);
            u.setLockedUntil(null);
            userRepository.save(u);
            return true;
        }).orElse(false);
    }

    @Transactional(readOnly = true)
    public InternalUserSummary summary() {
        return new InternalUserSummary(
                userRepository.count(),
                userRepository.countByEnabledTrue(),
                userRepository.countByLockedUntilAfter(LocalDateTime.now()));
    }

    private InternalUserView toView(User u) {
        return new InternalUserView(
                u.getId(),
                u.getUsername(),
                u.getEmail(),
                u.getPhone(),
                u.getFullName(),
                u.getRole().name(),
                u.isEmailVerified(),
                u.isEnabled(),
                u.isLocked(),
                u.getFailedLoginAttempts(),
                u.getLockedUntil(),
                u.getLastLoginAt(),
                u.getCreatedAt());
    }
}
