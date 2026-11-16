-- ============================================================================
-- V14: Milestone 6 Expiring Share Links Table
-- Author: Upcraft Stay Platform
-- Date: 12-11-2026
-- ============================================================================

CREATE TABLE IF NOT EXISTS share_links (
    id BINARY(16) NOT NULL PRIMARY KEY,
    organization_id BINARY(16) NOT NULL,
    property_id BINARY(16) NULL,
    created_by_user_id BINARY(16) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    resource_type VARCHAR(30) NOT NULL,
    resource_id BINARY(16) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6) NULL,
    view_count BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by VARCHAR(100) NULL DEFAULT 'SYSTEM',
    updated_at DATETIME(6) NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    updated_by VARCHAR(100) NULL,
    deleted_at DATETIME(6) NULL,
    deleted_by VARCHAR(100) NULL,
    status SMALLINT NOT NULL DEFAULT 1,
    audit_tracker_id VARCHAR(64) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_share_links_org FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT fk_share_links_property FOREIGN KEY (property_id) REFERENCES properties (id),
    CONSTRAINT fk_share_links_creator FOREIGN KEY (created_by_user_id) REFERENCES users (id),
    UNIQUE KEY uk_share_links_token_hash (token_hash),
    INDEX idx_share_links_org_created (organization_id, created_at),
    INDEX idx_share_links_resource (resource_type, resource_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

