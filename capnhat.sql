USE pbl4_monitor;

-- =======================================================
-- Hết hạn lệnh Remote (PENDING/SENT/RUNNING bị treo)
-- CHẠY FILE NÀY TRƯỚC KHI KHỞI ĐỘNG LẠI SERVER
-- (spring.jpa.hibernate.ddl-auto=none nên Hibernate không tự thêm cột)
-- =======================================================

ALTER TABLE remote_commands
    ADD COLUMN created_at DATETIME NULL;

-- Các lệnh cũ chưa có created_at: lần chạy đầu của scheduler sẽ tự
-- chuyển lệnh PENDING cũ sang TIMEOUT, không cần UPDATE thủ công.

CREATE INDEX idx_remote_commands_status
    ON remote_commands (status);