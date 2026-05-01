# SwiftPay API

> A production-grade Digital Wallet Platform built with Java 25 & Spring Boot 3.5 — inspired by MoMo / ZaloPay.

[![Java](https://img.shields.io/badge/Java-25-orange?logo=openjdk)](https://openjdk.org/projects/jdk/25/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.13-brightgreen?logo=springboot)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue?logo=postgresql)](https://www.postgresql.org/)
[![Redis](https://img.shields.io/badge/Redis-7-red?logo=redis)](https://redis.io/)
[![License](https://img.shields.io/badge/License-MIT-lightgrey)](LICENSE)

---

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Tech Stack](#tech-stack)
- [Architecture](#architecture)
- [Getting Started](#getting-started)
- [Environment Variables](#environment-variables)
- [API Reference](#api-reference)
- [Project Structure](#project-structure)
- [Security Design](#security-design)
- [Roadmap](#roadmap)

---

## Overview

SwiftPay is a RESTful backend for a digital wallet platform. Users can register, verify their email, top up via VNPay, transfer funds, and view transaction history.

---

## Features

### Implemented
- [x] Register / Login / Logout
- [x] JWT Authentication (Access Token 15m + Refresh Token 7d)
- [x] Email Verification (AWS SES)
- [x] Account Lockout after 5 failed attempts (15-minute cooldown)
- [x] Forgot / Reset Password (Redis token, 15-minute TTL)
- [x] Wallet auto-created on register
- [x] Refresh Token stored in Redis with TTL

### In Progress
- [ ] Top-up via VNPay (IPN callback verification)
- [ ] Peer-to-peer transfer with idempotency key
- [ ] Transaction history (filter, pagination, sort)
- [ ] PDF statement export (OpenPDF)
- [ ] KYC document upload (AWS S3)
- [ ] Audit log (MongoDB, append-only)
- [ ] Role-based access: `USER` / `ADMIN`
- [ ] Admin: freeze / unfreeze wallet

---

## Tech Stack

| Category | Technology | Version |
|---|---|---|
| Language | Java | 25 |
| Framework | Spring Boot | 3.5.13 |
| Security | Spring Security + jjwt | 6.5.x / 0.12.6 |
| Primary DB | PostgreSQL | 16 |
| ORM | Hibernate / Spring Data JPA | 6.6.x |
| Migration | Flyway | 11.7.x |
| Audit Log | MongoDB | 7.x |
| Cache | Redis (Lettuce) | 7.x |
| Email | JavaMailSender + Thymeleaf | — |
| Payment | VNPay Sandbox | — |
| PDF | OpenPDF | 2.0.3 |
| API Docs | SpringDoc OpenAPI (Swagger) | 2.8.4 |
| Testing | JUnit 5.12 + Mockito 5.17 + Testcontainers | — |
| Container | Docker + Docker Compose | — |

---

## Architecture

```
┌─────────────────────────────────────────────┐
│               SwiftPay API                  │
│  ┌──────────┐  ┌──────────┐  ┌───────────┐  │
│  │Controller│→ │ Service  │→ │Repository │  │
│  └──────────┘  └──────────┘  └───────────┘  │
│                     │                        │
│          ┌──────────┼──────────┐             │
│          ▼          ▼          ▼             │
│      PostgreSQL   Redis     MongoDB          │
│      (primary)   (cache)    (audit)          │
└─────────────────────────────────────────────┘
          │               │
          ▼               ▼
       VNPay           AWS SES
     (payment)         (email)
```

**Request flow:**
```
HTTP Request
  → JwtAuthenticationFilter  (validate Bearer token)
  → SecurityFilterChain      (authorize endpoint)
  → Controller               (@Valid request body)
  → Service                  (@Transactional business logic)
  → Repository               (JPA / Redis / MongoDB)
  → ApiResponse<T>           (consistent JSON format)
```

---

## Getting Started

### Prerequisites

- Java 25
- Docker & Docker Compose
- Maven 3.9.x

### 1. Clone the repository

```bash
git clone https://github.com/hoanghnt/swiftpay-api.git
cd swiftpay-api
```

### 2. Configure environment

```bash
cp .env.example .env
# Edit .env with your credentials (DB password, JWT secret, AWS SES, etc.)
```

### 3. Start infrastructure

```bash
docker compose up -d
# Starts PostgreSQL 16, MongoDB 7, Redis 7
```

### 4. Load environment variables

```powershell
# PowerShell
Get-Content .env | ForEach-Object {
    if ($_ -match '^\s*([^#=][^=]*)=(.*)$') {
        [System.Environment]::SetEnvironmentVariable($matches[1].Trim(), $matches[2].Trim(), 'Process')
    }
}
```

```bash
# Bash / Linux / macOS
export $(grep -v '^#' .env | xargs)
```

### 5. Run the application

```bash
./mvnw spring-boot:run "-Dspring-boot.run.profiles=local"
```

### 6. Access API documentation

| Resource | URL |
|---|---|
| Swagger UI | http://localhost:8080/api/swagger-ui.html |
| OpenAPI JSON | http://localhost:8080/api/v3/api-docs |
| Health Check | http://localhost:8080/api/actuator/health |

---

## Environment Variables

Copy `.env.example` to `.env` and fill in your values.

| Variable | Description | Required |
|---|---|---|
| `DB_HOST` | PostgreSQL host | Yes |
| `DB_PORT` | PostgreSQL port (default: 5432) | Yes |
| `DB_NAME` | Database name | Yes |
| `DB_USERNAME` | Database user | Yes |
| `DB_PASSWORD` | Database password | Yes |
| `JWT_SECRET` | HS512 secret key (min 64 chars) | Yes |
| `MAIL_HOST` | SMTP host (e.g. AWS SES endpoint) | Yes |
| `MAIL_PORT` | SMTP port (587 for STARTTLS) | Yes |
| `MAIL_USERNAME` | SMTP username | Yes |
| `MAIL_PASSWORD` | SMTP password | Yes |
| `MAIL_FROM` | Sender email address | Yes |
| `REDIS_HOST` | Redis host | No (default: localhost) |
| `REDIS_PORT` | Redis port | No (default: 6379) |
| `VNPAY_TMN_CODE` | VNPay terminal code | For payment feature |
| `VNPAY_HASH_SECRET` | VNPay HMAC secret | For payment feature |

---

## API Reference

All responses follow a consistent envelope format:

```json
{
  "success": true,
  "message": "Operation successful",
  "data": { },
  "errorCode": null,
  "timestamp": "2026-04-26T00:00:00"
}
```

### Authentication

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| `POST` | `/api/auth/register` | Public | Register new account |
| `GET` | `/api/auth/verify` | Public | Verify email (`?token=xxx`) |
| `POST` | `/api/auth/login` | Public | Login, receive JWT tokens |
| `POST` | `/api/auth/refresh` | Public | Refresh access token |
| `POST` | `/api/auth/logout` | Bearer | Revoke refresh token |
| `POST` | `/api/auth/forgot-password` | Public | Send reset link to email |
| `POST` | `/api/auth/reset-password` | Public | Set new password with token |

#### Register — `POST /api/auth/register`

```json
{
  "username": "john123",
  "email": "john@example.com",
  "phone": "0901234567",
  "password": "Password123",
  "fullName": "John Doe"
}
```

#### Login — `POST /api/auth/login`

```json
{
  "identifier": "john123",
  "password": "Password123"
}
```

Supports login by **username or email** via `identifier` field.

#### Error codes

| Code | HTTP | Description |
|---|---|---|
| `AUTH_001` | 401 | Invalid credentials |
| `AUTH_101` | 409 | Username already exists |
| `AUTH_102` | 409 | Email already exists |
| `AUTH_201` | 400 | Invalid verification token |
| `AUTH_301` | 423 | Account temporarily locked |
| `AUTH_303` | 403 | Email not verified |
| `AUTH_401` | 401 | Invalid refresh token |
| `AUTH_501` | 400 | Invalid or expired reset token |
| `VALID_001` | 400 | Validation failed |
| `SYS_001` | 500 | Internal server error |

---

## Project Structure

```
src/main/java/com/hoanghnt/swiftpay/
├── controller/                 REST endpoints
├── service/                    Business logic
├── repository/                 JPA + MongoDB repositories
├── entity/                     JPA entities
│   ├── User.java
│   ├── Wallet.java
│   ├── EmailVerification.java
│   └── Role.java
├── dto/
│   ├── request/                Inbound DTOs (Java Records + @Valid)
│   └── response/               Outbound DTOs (Java Records)
├── exception/
│   ├── ErrorCode.java          Centralized error registry
│   ├── GlobalExceptionHandler.java
│   └── custom/
│       ├── BusinessException.java
│       └── ResourceNotFoundException.java
├── config/
│   ├── SecurityConfig.java     Spring Security + CORS
│   ├── JwtProperties.java      @ConfigurationProperties
│   ├── JpaAuditingConfig.java
│   └── RedisConfig.java
├── security/
│   ├── JwtService.java         Token generation + validation
│   ├── JwtAuthenticationFilter.java
│   └── CustomUserDetailsService.java
└── SwiftPayApplication.java

src/main/resources/
├── db/migration/
│   └── V1__init_schema.sql     Flyway baseline schema
├── templates/
│   ├── email-verification.html
│   └── reset-password.html
├── application.yml             Base configuration
├── application-local.yml       Local overrides
└── application-test.yml        Test configuration (Testcontainers)
```

---

## Security Design

### Authentication Flow

```
POST /auth/login
  → Load user by username or email
  → Check: enabled → not locked → email verified
  → BCrypt verify password (strength 12)
  → On success: issue access token (15m) + refresh token (7d)
  → Store refresh token in Redis with TTL
  → On 5th failure: lock account for 15 minutes
```

### JWT

- Algorithm: **HS512**
- Access token: `15 minutes` — carries `role`, `userId`, `type: ACCESS`
- Refresh token: `7 days` — stored in Redis, revoked on logout
- Each token has a unique `jti` (UUID) for future blacklisting

### Password Security

- BCrypt with **cost factor 12** (~300ms/hash — brute-force resistant)
- Policy: min 8 chars, must include uppercase + lowercase + digit
- Password **never** appears in logs or API responses

### Account Lockout

- 5 consecutive failures → locked for 15 minutes
- Lock state stored in `users.locked_until` column
- Auto-unlock when `locked_until < NOW()`

---

## License

[MIT](LICENSE)
