-- V2: Normalize legacy enum columns to VARCHAR(50) and add audit/soft-delete columns safely.
-- Uses record_status on legacy tables so existing complaints.status / queries.status remain untouched.

ALTER TABLE users
    MODIFY COLUMN role VARCHAR(50) NOT NULL,
    ADD COLUMN created_by VARCHAR(100) NULL,
    ADD COLUMN updated_by VARCHAR(100) NULL,
    ADD COLUMN deleted_at DATETIME(6) NULL,
    ADD COLUMN deleted_by VARCHAR(100) NULL,
    ADD COLUMN record_status SMALLINT NOT NULL DEFAULT 1,
    ADD COLUMN audit_tracker_id VARCHAR(64) NULL,
    ADD INDEX idx_v2_users_role_active (role, active);

UPDATE users
SET record_status = CASE WHEN active = b'1' THEN 1 ELSE 0 END;

ALTER TABLE rooms
    ADD COLUMN created_by VARCHAR(100) NULL,
    ADD COLUMN updated_at DATETIME(6) NULL,
    ADD COLUMN updated_by VARCHAR(100) NULL,
    ADD COLUMN deleted_at DATETIME(6) NULL,
    ADD COLUMN deleted_by VARCHAR(100) NULL,
    ADD COLUMN record_status SMALLINT NOT NULL DEFAULT 1,
    ADD COLUMN audit_tracker_id VARCHAR(64) NULL;

ALTER TABLE meals
    MODIFY COLUMN type VARCHAR(50) NOT NULL,
    ADD COLUMN created_by VARCHAR(100) NULL,
    ADD COLUMN updated_at DATETIME(6) NULL,
    ADD COLUMN updated_by VARCHAR(100) NULL,
    ADD COLUMN deleted_at DATETIME(6) NULL,
    ADD COLUMN deleted_by VARCHAR(100) NULL,
    ADD COLUMN record_status SMALLINT NOT NULL DEFAULT 1,
    ADD COLUMN audit_tracker_id VARCHAR(64) NULL;

ALTER TABLE announcements
    ADD COLUMN created_by VARCHAR(100) NULL,
    ADD COLUMN updated_at DATETIME(6) NULL,
    ADD COLUMN updated_by VARCHAR(100) NULL,
    ADD COLUMN deleted_at DATETIME(6) NULL,
    ADD COLUMN deleted_by VARCHAR(100) NULL,
    ADD COLUMN record_status SMALLINT NOT NULL DEFAULT 1,
    ADD COLUMN audit_tracker_id VARCHAR(64) NULL;

ALTER TABLE complaints
    MODIFY COLUMN category VARCHAR(50) NOT NULL,
    MODIFY COLUMN priority VARCHAR(50) NOT NULL,
    MODIFY COLUMN status VARCHAR(50) NOT NULL,
    ADD COLUMN created_by VARCHAR(100) NULL,
    ADD COLUMN updated_by VARCHAR(100) NULL,
    ADD COLUMN deleted_at DATETIME(6) NULL,
    ADD COLUMN deleted_by VARCHAR(100) NULL,
    ADD COLUMN record_status SMALLINT NOT NULL DEFAULT 1,
    ADD COLUMN audit_tracker_id VARCHAR(64) NULL,
    ADD INDEX idx_v2_complaints_member_status (member_id, status);

ALTER TABLE queries
    MODIFY COLUMN category VARCHAR(50) NOT NULL,
    MODIFY COLUMN status VARCHAR(50) NOT NULL,
    ADD COLUMN created_by VARCHAR(100) NULL,
    ADD COLUMN updated_by VARCHAR(100) NULL,
    ADD COLUMN deleted_at DATETIME(6) NULL,
    ADD COLUMN deleted_by VARCHAR(100) NULL,
    ADD COLUMN record_status SMALLINT NOT NULL DEFAULT 1,
    ADD COLUMN audit_tracker_id VARCHAR(64) NULL;

ALTER TABLE query_replies
    MODIFY COLUMN sender_role VARCHAR(50) NOT NULL;

ALTER TABLE notifications
    ADD COLUMN created_by VARCHAR(100) NULL,
    ADD COLUMN updated_at DATETIME(6) NULL,
    ADD COLUMN updated_by VARCHAR(100) NULL,
    ADD COLUMN deleted_at DATETIME(6) NULL,
    ADD COLUMN deleted_by VARCHAR(100) NULL,
    ADD COLUMN record_status SMALLINT NOT NULL DEFAULT 1,
    ADD COLUMN audit_tracker_id VARCHAR(64) NULL,
    ADD INDEX idx_v2_notifications_user_read_created (user_id, is_read, created_at);

