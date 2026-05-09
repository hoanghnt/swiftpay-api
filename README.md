# SwiftPay API

> A production-grade Digital Wallet Platform built with Java 25 & Spring Boot 3.5 — inspired by MoMo / ZaloPay.

[![Java](https://img.shields.io/badge/Java-25-orange?logo=openjdk)](https://openjdk.org/projects/jdk/25/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.13-brightgreen?logo=springboot)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue?logo=postgresql)](https://www.postgresql.org/)
[![Redis](https://img.shields.io/badge/Redis-7-red?logo=redis)](https://redis.io/)
[![CI/CD](https://img.shields.io/github/actions/workflow/status/hoanghnt/swiftpay-api/ci.yml?label=CI%2FCD&logo=githubactions)](https://github.com/hoanghnt/swiftpay-api/actions)
[![License](https://img.shields.io/badge/License-MIT-lightgrey)](LICENSE)

---

## Live Demo

| Resource | URL |
|---|---|
| **Swagger UI** | https://swiftpay-api.onrender.com/api/swagger-ui.html |
| **Health Check** | https://swiftpay-api.onrender.com/api/actuator/health |
| **OpenAPI JSON** | https://swiftpay-api.onrender.com/api/v3/api-docs |

> **Note:** Hosted on Render free tier — first request may take ~30s to wake up.

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

SwiftPay is a RESTful backend for a digital wallet platform. Users can register, verify their email, top up via VNPay, transfer funds to other users, withdraw, and view transaction history — all secured by JWT with stateless session management.

---

## Features

### Implemented ✅

**Auth**
- [x] Register / Login / Logout
- [x] JWT Authentication — Access Token (15m) + Refresh Token (7d, stored in Redis)
- [x] Email Verification via AWS SES (async, Thymeleaf template)
- [x] Account Lockout after 5 failed attempts (15-minute cooldown)
- [x] Forgot / Reset Password (Redis token, 15-minute TTL)

**Wallet**
- [x] Wallet auto-created on register
- [x] View balance (`GET /wallet/me`)
- [x] Top-up via VNPay Sandbox — HMAC-SHA512 signed URL + IPN callback verification
- [x] Withdraw (mock bank transfer, 1% fee)
- [x] Freeze / Unfreeze wallet (Admin only)

**Transactions**
- [x] Peer-to-peer transfer with idempotency key (Redis TTL 24h)
- [x] Pessimistic locking with deadlock-safe lock ordering
- [x] Transaction history — filter by type/status/date, pagination, sort (whitelisted)
- [x] Get transaction by ID (ownership-enforced)

**Infrastructure**
- [x] Swagger UI with Bearer JWT authentication
- [x] CI/CD via GitHub Actions — build + auto-deploy to Render on merge to `main`
- [x] Docker multi-stage build (`eclipse-temurin:25`)
- [x] Flyway database migrations
- [x] Render PostgreSQL auto-configuration via `DATABASE_URL`
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
| Cache | Redis (Lettuce) | 7.x |
| Email | JavaMailSender + Thymeleaf | — |
| Payment | VNPay Sandbox | — |
| API Docs | SpringDoc OpenAPI (Swagger) | 2.8.4 |
| Container | Docker + Docker Compose | — |
| CI/CD | GitHub Actions | — |
| Deploy | Render.com | — |

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
│      (primary)   (cache)    (planned)        │
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
  → Repository               (JPA / Redis)
  → BaseResponse<T>          (consistent JSON format)
```

**VNPay top-up flow:**
```
POST /wallet/topup → generate HMAC-SHA512 signed URL → return to client
Client redirects to VNPay sandbox → user pays
VNPay → POST /payments/vnpay/ipn (server-to-server, no JWT)
  → verify HMAC signature
  → credit wallet balance
  → update transaction status to COMPLETED
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
| `VNPAY_RETURN_URL` | VNPay browser return URL | For payment feature |
| `VNPAY_IPN_URL` | VNPay IPN callback URL | For payment feature |

> On Render, `DATABASE_URL` is automatically parsed into `spring.datasource.*` by `RenderPostgresEnvironmentPostProcessor`.

---

## API Reference

All responses follow a consistent envelope:

```json
{
  "success": true,
  "message": "Operation successful",
  "data": { },
  "errorCode": null,
  "timestamp": "2026-05-09T10:00:00"
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

### Wallet

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| `GET` | `/api/wallet/me` | Bearer | View balance and wallet status |
| `POST` | `/api/wallet/topup` | Bearer | Create VNPay payment URL |
| `POST` | `/api/wallet/withdraw` | Bearer | Withdraw funds (mock, 1% fee) |
| `POST` | `/api/wallet/{userId}/freeze` | Bearer (Admin) | Freeze a wallet |
| `POST` | `/api/wallet/{userId}/unfreeze` | Bearer (Admin) | Unfreeze a wallet |

### Transactions

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| `POST` | `/api/transactions/transfer` | Bearer | Transfer funds P2P |
| `GET` | `/api/transactions` | Bearer | List transactions (filter + pagination) |
| `GET` | `/api/transactions/{id}` | Bearer | Get transaction detail |

### Payments (VNPay callbacks — no JWT)

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| `POST` | `/api/payments/vnpay/ipn` | Public | VNPay IPN server callback |
| `GET` | `/api/payments/vnpay/return` | Public | VNPay browser return URL |

### Example Requests

#### Register
```json
POST /api/auth/register
{
  "username": "john123",
  "email": "john@example.com",
  "phone": "0901234567",
  "password": "Password123",
  "fullName": "John Doe"
}
```

#### Login
```json
POST /api/auth/login
{
  "identifier": "john123",
  "password": "Password123"
}
```
Supports login by **username or email** via the `identifier` field.

#### Transfer
```json
POST /api/transactions/transfer
Headers:
  Authorization: Bearer <access_token>
  X-Idempotency-Key: <uuid>

{
  "receiverUsername": "jane456",
  "amount": 100000,
  "description": "Lunch payment"
}
```

#### List Transactions
```
GET /api/transactions?type=TRANSFER&status=COMPLETED&from=2026-01-01T00:00:00&to=2026-12-31T23:59:59&page=0&size=10&sortBy=createdAt&sortDir=desc
```

### Error Codes

| Code | HTTP | Description |
|---|---|---|
| `VALID_001` | 400 | Validation failed |
| `AUTH_001` | 401 | Invalid credentials |
| `AUTH_101` | 409 | Username already exists |
| `AUTH_102` | 409 | Email already exists |
| `AUTH_201` | 400 | Invalid verification token |
| `AUTH_301` | 423 | Account temporarily locked |
| `AUTH_303` | 403 | Email not verified |
| `AUTH_401` | 401 | Invalid refresh token |
| `AUTH_501` | 400 | Invalid or expired reset token |
| `WAL_001` | 404 | Wallet not found |
| `WAL_002` | 403 | Wallet is frozen |
| `WAL_003` | 400 | Insufficient balance |
| `WAL_004` | 400 | Cannot transfer to yourself |
| `WAL_005` | 409 | Wallet is already frozen |
| `WAL_006` | 409 | Wallet is not frozen |
| `TXN_001` | 409 | Duplicate transaction |
| `VNP_001` | 400 | Invalid VNPay signature |
| `SYS_001` | 500 | Internal server error |

---

## Project Structure

```
src/main/java/com/hoanghnt/swiftpay/
├── controller/
│   ├── AuthController.java
│   ├── WalletController.java
│   ├── TransactionController.java
│   └── PaymentController.java
├── service/
│   ├── AuthService.java
│   ├── WalletService.java
│   ├── TransactionService.java
│   ├── VNPayService.java
│   └── EmailService.java
├── repository/
│   ├── UserRepository.java
│   ├── WalletRepository.java
│   ├── TransactionRepository.java
│   ├── EmailVerificationRepository.java
│   └── specification/
│       └── TransactionSpecification.java
├── entity/
│   ├── User.java
│   ├── Wallet.java
│   ├── Transaction.java
│   ├── TransactionType.java
│   ├── TransactionStatus.java
│   ├── EmailVerification.java
│   └── Role.java
├── dto/
│   ├── request/                   Java Records + @Valid
│   │   ├── RegisterRequest.java
│   │   ├── LoginRequest.java
│   │   ├── TransferRequest.java
│   │   ├── TopupRequest.java
│   │   ├── WithdrawRequest.java
│   │   └── ...
│   └── response/                  Java Records
│       ├── BaseResponse.java
│       ├── WalletResponse.java
│       ├── TransactionResponse.java
│       ├── PageResponse.java
│       └── ...
├── exception/
│   ├── ErrorCode.java
│   ├── GlobalExceptionHandler.java
│   └── custom/
│       ├── BusinessException.java
│       └── ResourceNotFoundException.java
├── config/
│   ├── SecurityConfig.java
│   ├── OpenApiConfig.java
│   ├── VNPayConfig.java
│   ├── JwtProperties.java
│   ├── JpaAuditingConfig.java
│   ├── RedisConfig.java
│   └── RenderPostgresEnvironmentPostProcessor.java
├── security/
│   ├── JwtService.java
│   ├── JwtAuthenticationFilter.java
│   └── CustomUserDetailsService.java
└── SwiftPayApplication.java

src/main/resources/
├── db/migration/
│   └── V1__init_schema.sql
├── templates/
│   ├── email-verification.html
│   └── reset-password.html
├── application.yml
├── application-local.yml
└── application-prod.yml

.github/workflows/
└── ci.yml                         Build + deploy to Render on push to main
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

### Concurrency — Transfer Safety

- **Pessimistic locking** (`SELECT FOR UPDATE`) on both wallets during transfer
- **Deadlock-safe lock ordering** — wallets always locked in ascending UUID order
- **Idempotency key** (UUID, Redis TTL 24h) — prevents duplicate transactions on client retry

---

## CI/CD

```
Push to any branch → GitHub Actions: mvn package + docker build
PR to main         → same build (acts as merge gate)
Merge to main      → build → trigger Render deploy hook → Render redeploys
```
---

## License

[MIT](LICENSE)
