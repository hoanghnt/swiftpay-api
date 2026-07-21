-- Phase B (DB-per-service): tạo 3 database riêng cho auth/wallet/txn trong CÙNG 1 Postgres container (free).
-- Chạy TỰ ĐỘNG bởi docker-entrypoint-initdb.d KHI KHỞI TẠO volume postgres lần đầu (fresh `docker compose up`).
-- Nếu volume đã tồn tại, script KHÔNG chạy lại — khi đó tạo tay bằng psql (xem docs/phase-2/db-split-plan.md).
--
-- Postgres KHÔNG cho JOIN chéo database → ranh giới sở hữu bị ép cứng: mỗi service chỉ thấy bảng của nó.
-- Không có FK chéo service (users ở auth_db; wallets/transactions tham chiếu user_id là UUID thuần).
-- Monolith vẫn dùng swiftpay-db (POSTGRES_DB) làm legacy datasource, không sở hữu bảng nghiệp vụ nào.

CREATE DATABASE auth_db;
CREATE DATABASE wallet_db;
CREATE DATABASE txn_db;

-- ==================================================================== auth_db
\connect auth_db
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE users
(
    id                    UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    username              VARCHAR(50)  NOT NULL UNIQUE,
    email                 VARCHAR(100) NOT NULL UNIQUE,
    password              VARCHAR(255) NOT NULL,
    full_name             VARCHAR(100),
    phone                 VARCHAR(20) UNIQUE,
    role                  VARCHAR(20)  NOT NULL DEFAULT 'USER' CHECK (role IN ('USER', 'ADMIN')),
    email_verified        BOOLEAN      NOT NULL DEFAULT FALSE,
    enabled               BOOLEAN      NOT NULL DEFAULT TRUE,
    failed_login_attempts INT          NOT NULL DEFAULT 0,
    locked_until          TIMESTAMP,
    last_login_at         TIMESTAMP,
    version               BIGINT       NOT NULL DEFAULT 0,
    created_at            TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_users_email ON users (email);
CREATE INDEX idx_users_username ON users (username);

CREATE TABLE email_verifications
(
    id         UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    user_id    UUID         NOT NULL REFERENCES users (id),
    token      VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMP    NOT NULL,
    used_at    TIMESTAMP,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE auth_outbox
(
    id             UUID PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id   VARCHAR(100) NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    topic          VARCHAR(200) NOT NULL,
    payload        TEXT         NOT NULL,
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW(),
    published_at   TIMESTAMP
);
CREATE INDEX idx_auth_outbox_unpublished ON auth_outbox (created_at) WHERE published_at IS NULL;

-- ==================================================================== wallet_db
\connect wallet_db
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE wallets
(
    id         UUID PRIMARY KEY        DEFAULT gen_random_uuid(),
    user_id    UUID           NOT NULL UNIQUE,   -- UUID thuần, KHÔNG FK sang users (ở auth_db)
    balance    DECIMAL(19, 4) NOT NULL DEFAULT 0.0000 CHECK (balance >= 0),
    currency   VARCHAR(3)     NOT NULL DEFAULT 'VND',
    is_frozen  BOOLEAN        NOT NULL DEFAULT FALSE,
    version    BIGINT         NOT NULL DEFAULT 0,
    created_at TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP      NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_wallets_user_id ON wallets (user_id);

CREATE TABLE wallet_operations
(
    id           UUID PRIMARY KEY        DEFAULT gen_random_uuid(),
    op_key       VARCHAR(100)   NOT NULL UNIQUE,
    op_type      VARCHAR(20)    NOT NULL CHECK (op_type IN ('TRANSFER', 'CREDIT', 'DEBIT')),
    from_user_id UUID,
    to_user_id   UUID,
    amount       DECIMAL(19, 4) NOT NULL CHECK (amount > 0),
    created_at   TIMESTAMP      NOT NULL DEFAULT NOW()
);

-- ==================================================================== txn_db
\connect txn_db
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE transactions
(
    id                UUID PRIMARY KEY        DEFAULT gen_random_uuid(),
    idempotency_key   UUID           NOT NULL UNIQUE,
    sender_id         UUID,   -- UUID thuần, KHÔNG FK sang users (ở auth_db)
    receiver_id       UUID,
    sender_username   VARCHAR(255),   -- denormalized snapshot (sổ cái, không JOIN chéo)
    receiver_username VARCHAR(255),
    amount            DECIMAL(19, 4) NOT NULL CHECK (amount > 0),
    fee               DECIMAL(19, 4) NOT NULL DEFAULT 0.0000 CHECK (fee >= 0),
    type              VARCHAR(20)    NOT NULL CHECK (type IN ('TRANSFER', 'TOPUP', 'WITHDRAW')),
    status            VARCHAR(20)    NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED', 'REFUNDED')),
    description       VARCHAR(255),
    failure_reason    VARCHAR(255),
    vnp_txn_ref       VARCHAR(20),
    created_at        TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP      NOT NULL DEFAULT NOW(),
    CHECK (sender_id IS NULL OR receiver_id IS NULL OR sender_id != receiver_id)
);
CREATE INDEX idx_transactions_sender ON transactions (sender_id);
CREATE INDEX idx_transactions_receiver ON transactions (receiver_id);
CREATE INDEX idx_transactions_status ON transactions (status);
CREATE INDEX idx_transactions_created ON transactions (created_at);
CREATE UNIQUE INDEX uk_transactions_vnp_txn_ref ON transactions (vnp_txn_ref) WHERE vnp_txn_ref IS NOT NULL;

CREATE TABLE user_ref   -- read-model replicate qua event UserRegistered (nguồn sự thật vẫn là auth_db.users)
(
    id       UUID PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    email    VARCHAR(255)
);

CREATE TABLE txn_outbox
(
    id             UUID PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id   VARCHAR(100) NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    topic          VARCHAR(200) NOT NULL,
    payload        TEXT         NOT NULL,
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW(),
    published_at   TIMESTAMP
);
CREATE INDEX idx_txn_outbox_unpublished ON txn_outbox (created_at) WHERE published_at IS NULL;
