package com.hoanghnt.swiftpay.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "users")
@Getter
@Setter
@ToString(exclude = "password")
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable=false, unique=true, length=50)
    private String username;

    @Column(nullable=false, unique=true, length=100)
    private String email;

    @Column(unique=true, length=20)
    private String phone;

    @Column(nullable=false)
    private String password;

    @Column(name="full_name", length=100)
    private String fullName;
	
    @Builder.Default
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=20)
    private Role role = Role.USER;

    @Builder.Default
    @Column(name="email_verified", nullable=false)
    private boolean emailVerified = false;
    
    @Builder.Default
    @Column(nullable=false)
    private boolean enabled = true;

    @Builder.Default
    @Column(name="failed_login_attempts", nullable=false)
    private int failedLoginAttempts = 0;
    
    @Column(name="locked_until")
    private LocalDateTime lockedUntil;

    @Column(name="last_login_at")
    private LocalDateTime lastLoginAt;

    @CreatedDate @Column(name="created_at", nullable=false, updatable=false)
    private LocalDateTime createdAt;

    @LastModifiedDate @Column(name="updated_at", nullable=false)
    private LocalDateTime updatedAt;

    @Version
    private Long version;

    public boolean isLocked() {
        return lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now());
    }
}


