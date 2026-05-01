-- V1__init_schema.sql
-- SwiftPay — Initial Database Schema

-- Extension cho UUID generation
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ==================== USERS ====================
CREATE TABLE users
(
    id                    UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    username              VARCHAR(50)  NOT NULL UNIQUE,
    email                 VARCHAR(100) NOT NULL UNIQUE,
    password              VARCHAR(255) NOT NULL,
    full_name             VARCHAR(100),
    phone                 VARCHAR(20) UNIQUE,
    role                  VARCHAR(20)  NOT NULL DEFAULT 'USER'
        CHECK (role IN ('USER', 'ADMIN')),
    email_verified        BOOLEAN      NOT NULL DEFAULT FALSE,
    enabled               BOOLEAN      NOT NULL DEFAULT TRUE,
    failed_login_attempts INT          NOT NULL DEFAULT 0,
    locked_until          TIMESTAMP,
    last_login_at         TIMESTAMP,
    version               BIGINT       NOT NULL DEFAULT 0,
    created_at            TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON COLUMN users.failed_login_attempts IS 'Reset về 0 khi login thành công';
COMMENT ON COLUMN users.locked_until IS 'NULL = không bị khóa. Tự unlock khi locked_until < NOW()';
COMMENT ON COLUMN users.last_login_at IS 'Dùng cho audit và email cảnh báo đăng nhập lạ';

-- ==================== WALLETS ====================
CREATE TABLE wallets
(
    id         UUID PRIMARY KEY        DEFAULT gen_random_uuid(),
    user_id    UUID           NOT NULL UNIQUE REFERENCES users (id),
    balance    DECIMAL(19, 4) NOT NULL DEFAULT 0.0000
        CHECK (balance >= 0),
    currency   VARCHAR(3)     NOT NULL DEFAULT 'VND',
    is_frozen  BOOLEAN        NOT NULL DEFAULT FALSE,
    version    BIGINT         NOT NULL DEFAULT 0,
    created_at TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP      NOT NULL DEFAULT NOW()
);

-- ==================== TRANSACTIONS ====================
CREATE TABLE transactions
(
    id              UUID PRIMARY KEY        DEFAULT gen_random_uuid(),
    idempotency_key UUID           NOT NULL UNIQUE,
    sender_id       UUID REFERENCES users (id),
    receiver_id     UUID REFERENCES users (id),
    amount          DECIMAL(19, 4) NOT NULL CHECK (amount > 0),
    fee             DECIMAL(19, 4) NOT NULL DEFAULT 0.0000
        CHECK (fee >= 0),
    type            VARCHAR(20)    NOT NULL
        CHECK (type IN ('TRANSFER', 'TOPUP', 'WITHDRAW')),
    status          VARCHAR(20)    NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED', 'REFUNDED')),
    description     VARCHAR(255),
    failure_reason  VARCHAR(255),
    vnp_txn_ref     VARCHAR(20),              -- thêm dòng này
    created_at      TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP      NOT NULL DEFAULT NOW(),
    CHECK (sender_id IS NULL OR receiver_id IS NULL OR sender_id != receiver_id)
);

-- ==================== PAYMENTS (VNPay) ====================
CREATE TABLE payments
(
    id             UUID PRIMARY KEY        DEFAULT gen_random_uuid(),
    transaction_id UUID           NOT NULL REFERENCES transactions (id),
    amount         DECIMAL(19, 4) NOT NULL CHECK (amount > 0),
    method         VARCHAR(20)    NOT NULL DEFAULT 'VNPAY'
        CHECK (method IN ('VNPAY', 'MOMO', 'ZALOPAY', 'BANK_TRANSFER')),
    vnpay_txn_ref  VARCHAR(100),
    vnpay_txn_no   VARCHAR(100),
    status         VARCHAR(20)    NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED', 'REFUNDED')),
    paid_at        TIMESTAMP,
    created_at     TIMESTAMP      NOT NULL DEFAULT NOW()
);

-- ==================== EMAIL VERIFICATIONS ====================
CREATE TABLE email_verifications
(
    id         UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    user_id    UUID         NOT NULL REFERENCES users (id),
    token      VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMP    NOT NULL,
    used_at    TIMESTAMP,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- ==================== KYC ====================
CREATE TABLE kyc_documents
(
    id            UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    user_id       UUID         NOT NULL REFERENCES users (id),
    document_type VARCHAR(30)  NOT NULL DEFAULT 'CCCD'
        CHECK (document_type IN ('CCCD', 'PASSPORT', 'DRIVER_LICENSE')),
    file_url      VARCHAR(500) NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    reviewed_at   TIMESTAMP,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- ==================== INDEXES ====================
CREATE INDEX idx_users_email ON users (email);
CREATE INDEX idx_users_username ON users (username);
CREATE INDEX idx_wallets_user_id ON wallets (user_id);
CREATE INDEX idx_transactions_sender ON transactions (sender_id);
CREATE INDEX idx_transactions_receiver ON transactions (receiver_id);
CREATE INDEX idx_transactions_status ON transactions (status);
CREATE INDEX idx_transactions_created ON transactions (created_at);
CREATE INDEX idx_payments_transaction_id ON payments (transaction_id);