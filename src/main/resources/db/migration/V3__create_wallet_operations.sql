-- V3__create_wallet_operations.sql
-- Milestone 6 — Tầng idempotency thứ 2 (wallet-op level) do wallet-service sở hữu.
-- Mỗi money-movement (transfer/credit/debit) ghi đúng một dòng với op_key UNIQUE. Bảng này cũng là
-- nguồn đối chiếu cho reconciliation của transaction-service ("op_key này đã apply chưa?").
-- Monolith giữ quyền Flyway; wallet-service chạy ddl-auto=validate nên cần bảng này tồn tại trước.

CREATE TABLE wallet_operations
(
    id           UUID PRIMARY KEY        DEFAULT gen_random_uuid(),
    op_key       VARCHAR(100)   NOT NULL UNIQUE,
    op_type      VARCHAR(20)    NOT NULL
        CHECK (op_type IN ('TRANSFER', 'CREDIT', 'DEBIT')),
    from_user_id UUID REFERENCES users (id),
    to_user_id   UUID REFERENCES users (id),
    amount       DECIMAL(19, 4) NOT NULL CHECK (amount > 0),
    created_at   TIMESTAMP      NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE wallet_operations IS 'Idempotency ledger tầng-wallet (Milestone 6). op_key = transaction.id do transaction-service cấp.';
COMMENT ON COLUMN wallet_operations.op_key IS 'Khóa idempotency; chống double-apply khi transaction-service retry.';
