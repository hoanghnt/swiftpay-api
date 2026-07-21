-- Milestone 8 / Outbox: Transactional Outbox cho auth-service và transaction-service.
-- Listener ghi 1 dòng outbox TRONG CÙNG transaction nghiệp vụ (atomic với user/transaction) → OutboxPublisher
-- (@Scheduled) poll các dòng chưa publish, gửi Kafka, đánh dấu published_at (retry nếu Kafka down).
--
-- BẮT BUỘC trước Phase B: khi còn chung 1 DB, self-heal (consumer đọc lại nguồn) che được mất event; tách DB
-- rồi thì mất UserRegistered = user_ref của txn thiếu user VĨNH VIỄN → transfer tới user đó fail.
--
-- Mỗi service sở hữu bảng outbox riêng. Tên qualified (auth_/txn_) để cùng tồn tại trong swiftpay-db giai đoạn
-- quá độ; khi tách DB (Phase B) mỗi bảng nằm trong DB của service tương ứng (Flyway của service sẽ sở hữu nó).
-- Monolith giữ quyền Flyway ở giai đoạn này; auth/txn chạy ddl-auto=validate nên cần bảng tồn tại trước.

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
-- Partial index: publisher chỉ quét dòng chưa publish, sắp theo thứ tự tạo.
CREATE INDEX idx_auth_outbox_unpublished ON auth_outbox (created_at) WHERE published_at IS NULL;

COMMENT ON TABLE auth_outbox IS 'Transactional outbox của auth-service (UserRegistered). Publisher poll published_at IS NULL.';

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

COMMENT ON TABLE txn_outbox IS 'Transactional outbox của transaction-service (TransactionCompleted, PaymentSucceeded).';
