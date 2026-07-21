-- Milestone 8 / A2: transaction-service bỏ FK @ManyToOne sang users.
-- Thêm username denormalized (snapshot sổ cái) + read-model user_ref (replicate qua event UserRegistered).
-- FK sender_id/receiver_id giữ nguyên ở phase này (chỉ drop khi tách DB thật — Phase B/C).
-- Idempotent (IF NOT EXISTS) để an toàn nếu đã áp thủ công.

ALTER TABLE transactions ADD COLUMN IF NOT EXISTS sender_username   VARCHAR(255);
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS receiver_username VARCHAR(255);

-- Backfill username cho các record cũ (users vẫn cùng DB ở phase này).
UPDATE transactions t
SET sender_username = u.username
FROM users u
WHERE u.id = t.sender_id AND t.sender_username IS NULL;

UPDATE transactions t
SET receiver_username = u.username
FROM users u
WHERE u.id = t.receiver_id AND t.receiver_username IS NULL;

-- Read-model cục bộ của transaction-service (nguồn sự thật vẫn là auth-service).
CREATE TABLE IF NOT EXISTS user_ref (
    id       UUID PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    email    VARCHAR(255)
);

-- Seed từ users hiện có để transfer resolve receiver ngay, không phải chờ replay Kafka.
INSERT INTO user_ref (id, username, email)
SELECT id, username, email FROM users
ON CONFLICT (id) DO NOTHING;
