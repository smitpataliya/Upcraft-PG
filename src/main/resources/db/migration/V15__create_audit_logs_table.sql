-- ============================================================================
-- V15: Milestone 7 Immutable Audit Logs Table
-- Author: Upcraft Stay Platform
-- Date: 12-11-2026
-- ============================================================================

CREATE TABLE IF NOT EXISTS audit_logs (
    id BINARY(16) NOT NULL PRIMARY KEY,
    organization_id BINARY(16) NULL,
    property_id BINARY(16) NULL,
    actor_user_id BINARY(16) NULL,
    actor_email VARCHAR(255) NULL,
    actor_role VARCHAR(40) NULL,
    action VARCHAR(80) NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id BINARY(16) NULL,
    summary VARCHAR(500) NOT NULL,
    metadata_json TEXT NULL,
    client_ip VARCHAR(64) NULL,
    trace_id VARCHAR(64) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by VARCHAR(100) NULL DEFAULT 'SYSTEM',
    updated_at DATETIME(6) NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    updated_by VARCHAR(100) NULL,
    deleted_at DATETIME(6) NULL,
    deleted_by VARCHAR(100) NULL,
    status SMALLINT NOT NULL DEFAULT 1,
    audit_tracker_id VARCHAR(64) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_audit_logs_org FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT fk_audit_logs_property FOREIGN KEY (property_id) REFERENCES properties (id),
    CONSTRAINT fk_audit_logs_actor FOREIGN KEY (actor_user_id) REFERENCES users (id),
    INDEX idx_audit_logs_org_created (organization_id, created_at),
    INDEX idx_audit_logs_prop_created (property_id, created_at),
    INDEX idx_audit_logs_action_created (action, created_at),
    INDEX idx_audit_logs_entity (entity_type, entity_id),
    INDEX idx_audit_logs_actor_created (actor_user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

