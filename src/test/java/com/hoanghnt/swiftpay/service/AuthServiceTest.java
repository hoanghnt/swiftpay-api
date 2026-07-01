package com.hoanghnt.swiftpay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;

import com.hoanghnt.swiftpay.audit.AuditService;
import com.hoanghnt.swiftpay.config.JwtProperties;
import com.hoanghnt.swiftpay.dto.request.LoginRequest;
import com.hoanghnt.swiftpay.dto.request.RegisterRequest;
import com.hoanghnt.swiftpay.dto.response.LoginResponse;
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

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock WalletRepository walletRepository;
    @Mock org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    @Mock EmailVerificationRepository emailVerificationRepository;
    @Mock EmailService emailService;
    @Mock JwtService jwtService;
    @Mock AuthenticationManager authenticationManager;
    @Mock RedisTemplate<String, String> redisTemplate;
    @Mock ValueOperations<String, String> valueOps;
    @Mock JwtProperties jwtProperties;
    @Mock AuditService auditService;

    @InjectMocks AuthService authService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        lenient().when(jwtProperties.getRefreshTokenExpiration()).thenReturn(Duration.ofDays(7));
        lenient().when(jwtProperties.getAccessTokenExpiration()).thenReturn(Duration.ofMinutes(15));
    }

    private User buildUser(String username) {
        return User.builder()
                .id(UUID.randomUUID())
                .username(username)
                .email(username + "@test.com")
                .password("hashed")
                .role(Role.USER)
                .emailVerified(true)
                .enabled(true)
                .failedLoginAttempts(0)
                .build();
    }

    // ==================== login() ====================

    @Test
    void login_happyPath_shouldReturnLoginResponseWithTokens() {
        User user = buildUser("alice");
        LoginRequest request = new LoginRequest("alice", "Password123");

        when(userRepository.findByUsernameOrEmail("alice", "alice")).thenReturn(Optional.of(user));
        when(jwtService.generateAccessToken(user)).thenReturn("access-token");
        when(jwtService.generateRefreshToken(user)).thenReturn("refresh-token");

        LoginResponse response = authService.login(request);

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.username()).isEqualTo("alice");
        verify(valueOps).set(eq("refresh:" + user.getId()), eq("refresh-token"), any(Duration.class));
    }

    @Test
    void login_whenUserNotFound_shouldThrowInvalidCredentials() {
        when(userRepository.findByUsernameOrEmail("ghost", "ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("ghost", "whatever")))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_CREDENTIALS));
    }

    @Test
    void login_whenAccountLocked_shouldThrowAccountLocked() {
        User user = buildUser("alice");
        user.setLockedUntil(LocalDateTime.now().plusMinutes(10));

        when(userRepository.findByUsernameOrEmail("alice", "alice")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest("alice", "Password123")))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.ACCOUNT_LOCKED));
    }

    @Test
    void login_whenAccountDisabled_shouldThrowAccountDisabled() {
        User user = buildUser("alice");
        user.setEnabled(false);

        when(userRepository.findByUsernameOrEmail("alice", "alice")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest("alice", "Password123")))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.ACCOUNT_DISABLED));
    }

    @Test
    void login_whenEmailNotVerified_shouldThrowEmailNotVerified() {
        User user = buildUser("alice");
        user.setEmailVerified(false);

        when(userRepository.findByUsernameOrEmail("alice", "alice")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest("alice", "Password123")))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.EMAIL_NOT_VERIFIED));
    }

    @Test
    void login_whenBadPassword_shouldHandleFailedLoginAndThrowInvalidCredentials() {
        User user = buildUser("alice");

        when(userRepository.findByUsernameOrEmail("alice", "alice")).thenReturn(Optional.of(user));
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));

        assertThatThrownBy(() -> authService.login(new LoginRequest("alice", "wrong")))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_CREDENTIALS));

        assertThat(user.getFailedLoginAttempts()).isEqualTo(1);
        verify(userRepository).save(user);
    }

    @Test
    void login_whenFifthBadPassword_shouldLockAccount() {
        User user = buildUser("alice");
        user.setFailedLoginAttempts(4);

        when(userRepository.findByUsernameOrEmail("alice", "alice")).thenReturn(Optional.of(user));
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));

        assertThatThrownBy(() -> authService.login(new LoginRequest("alice", "wrong")))
                .isInstanceOf(BusinessException.class);

        assertThat(user.getFailedLoginAttempts()).isEqualTo(5);
        assertThat(user.getLockedUntil()).isNotNull();
    }

    @Test
    void login_success_shouldResetFailedLoginAttempts() {
        User user = buildUser("alice");
        user.setFailedLoginAttempts(3);

        when(userRepository.findByUsernameOrEmail("alice", "alice")).thenReturn(Optional.of(user));

        authService.login(new LoginRequest("alice", "Password123"));

        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLockedUntil()).isNull();
    }

    @Test
    void login_success_shouldStoreRefreshTokenInRedis() {
        User user = buildUser("alice");
        when(userRepository.findByUsernameOrEmail("alice", "alice")).thenReturn(Optional.of(user));
        when(jwtService.generateRefreshToken(user)).thenReturn("refresh-token");

        authService.login(new LoginRequest("alice", "Password123"));

        verify(valueOps).set(eq("refresh:" + user.getId()), eq("refresh-token"), any(Duration.class));
    }

    // ==================== register() ====================

    @Test
    void register_happyPath_shouldSaveUserCreateWalletAndSendEmail() {
        RegisterRequest request = new RegisterRequest("alice", "alice@test.com", "0901234567", "Password123", "Alice");

        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.existsByEmail("alice@test.com")).thenReturn(false);
        when(userRepository.existsByPhone("0901234567")).thenReturn(false);
        when(passwordEncoder.encode("Password123")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });

        RegisterResponse response = authService.register(request);

        assertThat(response.username()).isEqualTo("alice");
        verify(walletRepository).save(any(Wallet.class));
        verify(emailVerificationRepository).save(any(EmailVerification.class));
        verify(emailService).sendVerificationEmail(eq("alice@test.com"), eq("alice"), anyString());
    }

    @Test
    void register_whenUsernameExists_shouldThrowUsernameAlreadyExists() {
        RegisterRequest request = new RegisterRequest("alice", "alice@test.com", "0901234567", "Password123", "Alice");
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.USERNAME_ALREADY_EXISTS));
    }

    @Test
    void register_whenEmailExists_shouldThrowEmailAlreadyExists() {
        RegisterRequest request = new RegisterRequest("alice", "alice@test.com", "0901234567", "Password123", "Alice");
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.existsByEmail("alice@test.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS));
    }

    @Test
    void register_whenPhoneExists_shouldThrowPhoneAlreadyExists() {
        RegisterRequest request = new RegisterRequest("alice", "alice@test.com", "0901234567", "Password123", "Alice");
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.existsByEmail("alice@test.com")).thenReturn(false);
        when(userRepository.existsByPhone("0901234567")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.PHONE_ALREADY_EXISTS));
    }

    @Test
    void register_shouldCreateWalletForNewUser() {
        RegisterRequest request = new RegisterRequest("alice", "alice@test.com", "0901234567", "Password123", "Alice");
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.existsByEmail("alice@test.com")).thenReturn(false);
        when(userRepository.existsByPhone("0901234567")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.register(request);

        verify(walletRepository, times(1)).save(any(Wallet.class));
    }

    // ==================== logout() ====================

    @Test
    void logout_shouldDeleteRefreshTokenFromRedis() {
        User user = buildUser("alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        authService.logout("alice");

        verify(redisTemplate).delete("refresh:" + user.getId());
    }

    @Test
    void logout_whenUserNotFound_shouldNotThrow() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        authService.logout("ghost");

        verify(redisTemplate, never()).delete(anyString());
    }

    // ==================== resetPassword() ====================

    @Test
    void resetPassword_happyPath_shouldUpdatePasswordAndDeleteTokens() {
        User user = buildUser("alice");
        String token = "reset-token";

        when(valueOps.get("reset:" + token)).thenReturn(user.getId().toString());
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("NewPassword123")).thenReturn("new-hashed");

        authService.resetPassword(token, "NewPassword123");

        assertThat(user.getPassword()).isEqualTo("new-hashed");
        verify(userRepository).save(user);
        verify(redisTemplate).delete("reset:" + token);
        verify(redisTemplate).delete("refresh:" + user.getId());
    }

    @Test
    void resetPassword_whenTokenInvalid_shouldThrowInvalidResetToken() {
        when(valueOps.get("reset:bad-token")).thenReturn(null);

        assertThatThrownBy(() -> authService.resetPassword("bad-token", "NewPassword123"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_RESET_TOKEN));
    }

    @Test
    void resetPassword_afterReset_shouldRemoveRefreshTokenToForceLogout() {
        User user = buildUser("alice");
        String token = "reset-token";

        when(valueOps.get("reset:" + token)).thenReturn(user.getId().toString());
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        authService.resetPassword(token, "NewPassword123");

        verify(redisTemplate).delete("refresh:" + user.getId());
    }
}
