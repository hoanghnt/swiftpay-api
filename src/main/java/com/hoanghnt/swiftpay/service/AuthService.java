package com.hoanghnt.swiftpay.service;

import com.hoanghnt.swiftpay.config.JwtProperties;
import com.hoanghnt.swiftpay.dto.request.LoginRequest;
import com.hoanghnt.swiftpay.dto.request.RegisterRequest;
import com.hoanghnt.swiftpay.dto.response.LoginResponse;
import com.hoanghnt.swiftpay.dto.response.RefreshTokenResponse;
import com.hoanghnt.swiftpay.dto.response.RegisterResponse;
import com.hoanghnt.swiftpay.entity.EmailVerification;
import com.hoanghnt.swiftpay.entity.Role;
import com.hoanghnt.swiftpay.entity.User;
import com.hoanghnt.swiftpay.entity.Wallet;
import com.hoanghnt.swiftpay.exception.ErrorCode;
import com.hoanghnt.swiftpay.exception.custom.BusinessException;
import com.hoanghnt.swiftpay.repository.EmailVerificationRepository;
import com.hoanghnt.swiftpay.repository.UserRepository;
import com.hoanghnt.swiftpay.repository.WalletRepository;
import com.hoanghnt.swiftpay.security.JwtService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationRepository emailVerificationRepository;
    private final EmailService emailService;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final RedisTemplate<String, String> redisTemplate;
    private final JwtProperties jwtProperties;

    private static final int VERIFICATION_TOKEN_EXPIRATION_HOURS = 24;
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final String REFRESH_TOKEN_PREFIX = "refresh:";
    private static final String RESET_TOKEN_PREFIX = "reset:";
    private static final int RESET_TOKEN_EXPIRATION_MINUTES = 15;

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        // 1. Check duplicates
        if (userRepository.existsByUsername(request.username())) {
            throw new BusinessException(ErrorCode.USERNAME_ALREADY_EXISTS);
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        if (request.phone() != null && userRepository.existsByPhone(request.phone())) {
            throw new BusinessException(ErrorCode.PHONE_ALREADY_EXISTS);
        }

        // 2. Create user
        User user = User.builder()
                .username(request.username())
                .email(request.email())
                .phone(request.phone())
                .password(passwordEncoder.encode(request.password()))
                .fullName(request.fullName())
                .role(Role.USER)
                .build();
        user = userRepository.save(user);

        // 3. Create wallet
        Wallet wallet = Wallet.builder()
                .user(user)
                .build();
        walletRepository.save(wallet);

        // 4. Create email verification token
        String token = UUID.randomUUID().toString();
        EmailVerification verification = EmailVerification.builder()
                .user(user)
                .token(token)
                .expiresAt(LocalDateTime.now().plusHours(VERIFICATION_TOKEN_EXPIRATION_HOURS))
                .build();
        emailVerificationRepository.save(verification);

        // 5. Send email
        emailService.sendVerificationEmail(user.getEmail(), user.getUsername(), token);
        log.info("User registered: username={}, email={}", user.getUsername(), user.getEmail());

        // 6. Return response
        return new RegisterResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                "Registration successful. Please check your email to verify your account.");
    }

    @Transactional
    public void verifyEmail(String token) {
        EmailVerification verification = emailVerificationRepository.findByToken(token)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_VERIFICATION_TOKEN));
        if (verification.isUsed()) {
            throw new BusinessException(ErrorCode.VERIFICATION_TOKEN_ALREADY_USED);
        }
        if (verification.isExpired()) {
            throw new BusinessException(ErrorCode.VERIFICATION_TOKEN_EXPIRED);
        }
        User user = verification.getUser();
        user.setEmailVerified(true);
        userRepository.save(user);
        verification.setUsedAt(LocalDateTime.now());
        emailVerificationRepository.save(verification);
        log.info("Email verified for user: {}", user.getUsername());
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        // 1. Find user
        User user = userRepository.findByUsernameOrEmail(request.identifier(), request.identifier())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        // 2. Check account status
        if (!user.isEnabled()) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }
        if (user.isLocked()) {
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
        }
        if (!user.isEmailVerified()) {
            throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED);
        }

        // 3. Authenticate password
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(user.getUsername(), request.password()));
        } catch (BadCredentialsException e) {
            handleFailedLogin(user);
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        // 4. Reset failed attempts + update lastLoginAt
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        // 5. Generate tokens
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        // 6. Store refresh token in Redis
        long refreshTtlSeconds = jwtProperties.getRefreshTokenExpiration().toSeconds();
        redisTemplate.opsForValue().set(
                REFRESH_TOKEN_PREFIX + user.getId(),
                refreshToken,
                Duration.ofSeconds(refreshTtlSeconds));

        log.info("User logged in: username={}", user.getUsername());

        return new LoginResponse(
                accessToken,
                refreshToken,
                "Bearer",
                jwtProperties.getAccessTokenExpiration().toSeconds(),
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole().name());
    }

    private void handleFailedLogin(User user) {
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);
        if (attempts >= MAX_FAILED_ATTEMPTS) {
            user.setLockedUntil(LocalDateTime.now().plusMinutes(15));
            log.warn("Account locked after {} failed attempts: {}", attempts, user.getUsername());
        }
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public RefreshTokenResponse refresh(String refreshToken) {
        // 1. Validate JWT signature + expiration
        String username;
        String userId;
        String tokenType;
        try {
            username = jwtService.extractUsername(refreshToken);
            userId = jwtService.extractUserId(refreshToken);
            tokenType = jwtService.extractTokenType(refreshToken);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        // 2. Check token type
        if (!"REFRESH".equals(tokenType)) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        // 3. Check Redis
        String storedToken = redisTemplate.opsForValue().get(REFRESH_TOKEN_PREFIX + userId);
        if (storedToken == null || !storedToken.equals(refreshToken)) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        // 4. Load user
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));

        // 5. Generate new access token
        String newAccessToken = jwtService.generateAccessToken(user);

        return new RefreshTokenResponse(
                newAccessToken,
                "Bearer",
                jwtProperties.getAccessTokenExpiration().toSeconds());
    }

    public void logout(String username) {
        userRepository.findByUsername(username).ifPresent(user -> {
            redisTemplate.delete(REFRESH_TOKEN_PREFIX + user.getId());
            log.info("User logged out: username={}", username);
        });
    }

    public void forgotPassword(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            // Generate token
            String token = UUID.randomUUID().toString();
            redisTemplate.opsForValue().set(
                    RESET_TOKEN_PREFIX + token,
                    user.getId().toString(),
                    Duration.ofMinutes(RESET_TOKEN_EXPIRATION_MINUTES));

            emailService.sendResetPasswordEmail(email, user.getUsername(), token);
            log.info("Password reset requested for: {}", email);
        });
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        // 1. Get user from Redis
        String userId = redisTemplate.opsForValue().get(RESET_TOKEN_PREFIX + token);
        if (userId == null) {
            throw new BusinessException(ErrorCode.INVALID_RESET_TOKEN);
        }

        // 2. Load user
        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_RESET_TOKEN));

        // 3. Hash new password + save
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // 4. Delete reset token (single-use)
        redisTemplate.delete(RESET_TOKEN_PREFIX + token);

        // 5. Delete refresh token → force logout all session
        redisTemplate.delete(REFRESH_TOKEN_PREFIX + user.getId());

        log.info("Password reset successful for: {}", user.getUsername());
    }
}