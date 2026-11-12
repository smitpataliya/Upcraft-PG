-- ============================================================================
-- V13: Milestone 5 Operations Tables & Legacy Operational Schema Evolution
-- Author: Upcraft Stay Platform
-- Date: 12-11-2026
-- ============================================================================

-- 1. Evolve complaints for multi-property, tenancy, assignment, and SLA tracking
ALTER TABLE complaints
    ADD COLUMN organization_id BINARY(16) NULL AFTER id,
    ADD COLUMN property_id BINARY(16) NULL AFTER organization_id,
    ADD COLUMN tenant_id BINARY(16) NULL AFTER property_id,
    ADD COLUMN tenancy_id BINARY(16) NULL AFTER tenant_id,
    ADD COLUMN room_id BINARY(16) NULL AFTER tenancy_id,
    ADD COLUMN bed_id BINARY(16) NULL AFTER room_id,
    ADD COLUMN assigned_to_user_id BINARY(16) NULL AFTER member_id,
    ADD COLUMN preferred_contact_time VARCHAR(80) NULL AFTER priority,
    ADD COLUMN resolution_summary TEXT NULL AFTER preferred_contact_time,
    ADD COLUMN acknowledged_at DATETIME(6) NULL AFTER resolution_summary,
    ADD COLUMN resolved_at DATETIME(6) NULL AFTER acknowledged_at,
    ADD COLUMN closed_at DATETIME(6) NULL AFTER resolved_at,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 AFTER audit_tracker_id,
    ADD KEY idx_complaints_org_prop_status (organization_id, property_id, status, created_at),
    ADD KEY idx_complaints_assigned_status (assigned_to_user_id, status),
    ADD KEY idx_complaints_tenant (tenant_id, created_at),
    ADD CONSTRAINT fk_complaints_org FOREIGN KEY (organization_id) REFERENCES organizations (id),
    ADD CONSTRAINT fk_complaints_property FOREIGN KEY (property_id) REFERENCES properties (id),
    ADD CONSTRAINT fk_complaints_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    ADD CONSTRAINT fk_complaints_tenancy FOREIGN KEY (tenancy_id) REFERENCES tenancies (id),
    ADD CONSTRAINT fk_complaints_room FOREIGN KEY (room_id) REFERENCES rooms (id),
    ADD CONSTRAINT fk_complaints_bed FOREIGN KEY (bed_id) REFERENCES beds (id),
    ADD CONSTRAINT fk_complaints_assignee FOREIGN KEY (assigned_to_user_id) REFERENCES users (id);

UPDATE complaints c
LEFT JOIN tenants t ON t.user_id = c.member_id AND t.status <> 9
LEFT JOIN tenancies tn ON tn.tenant_id = t.id AND tn.status <> 9 AND tn.tenancy_status = 'ACTIVE'
LEFT JOIN organization_members om ON om.user_id = c.member_id AND om.status <> 9
SET
    c.tenant_id = COALESCE(c.tenant_id, t.id),
    c.tenancy_id = COALESCE(c.tenancy_id, tn.id),
    c.room_id = COALESCE(c.room_id, tn.room_id),
    c.bed_id = COALESCE(c.bed_id, tn.bed_id),
    c.property_id = COALESCE(c.property_id, tn.property_id, t.property_id),
    c.organization_id = COALESCE(
        c.organization_id,
        tn.organization_id,
        t.organization_id,
        om.organization_id,
        (SELECT o.id FROM organizations o WHERE o.status <> 9 ORDER BY o.created_at ASC LIMIT 1)
    )
WHERE c.organization_id IS NULL;

CREATE TABLE IF NOT EXISTS complaint_comments (
    id BINARY(16) NOT NULL PRIMARY KEY,
    organization_id BINARY(16) NOT NULL,
    complaint_id BINARY(16) NOT NULL,
    author_user_id BINARY(16) NOT NULL,
    message TEXT NOT NULL,
    is_internal BIT(1) NOT NULL DEFAULT b'0',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by VARCHAR(100) NULL DEFAULT 'SYSTEM',
    updated_at DATETIME(6) NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    updated_by VARCHAR(100) NULL,
    deleted_at DATETIME(6) NULL,
    deleted_by VARCHAR(100) NULL,
    status SMALLINT NOT NULL DEFAULT 1,
    audit_tracker_id VARCHAR(64) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_cc_org FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT fk_cc_complaint FOREIGN KEY (complaint_id) REFERENCES complaints (id),
    CONSTRAINT fk_cc_author FOREIGN KEY (author_user_id) REFERENCES users (id),
    INDEX idx_cc_complaint_created (complaint_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS complaint_attachments (
    id BINARY(16) NOT NULL PRIMARY KEY,
    organization_id BINARY(16) NOT NULL,
    complaint_id BINARY(16) NOT NULL,
    comment_id BINARY(16) NULL,
    stored_file_id BINARY(16) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by VARCHAR(100) NULL DEFAULT 'SYSTEM',
    updated_at DATETIME(6) NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    updated_by VARCHAR(100) NULL,
    deleted_at DATETIME(6) NULL,
    deleted_by VARCHAR(100) NULL,
    status SMALLINT NOT NULL DEFAULT 1,
    audit_tracker_id VARCHAR(64) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_ca_org FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT fk_ca_complaint FOREIGN KEY (complaint_id) REFERENCES complaints (id),
    CONSTRAINT fk_ca_comment FOREIGN KEY (comment_id) REFERENCES complaint_comments (id),
    CONSTRAINT fk_ca_file FOREIGN KEY (stored_file_id) REFERENCES stored_files (id),
    INDEX idx_ca_complaint (complaint_id),
    INDEX idx_ca_file (stored_file_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Evolve announcements for org/property/audience scoping, schedule window, and attachments
ALTER TABLE announcements
    ADD COLUMN organization_id BINARY(16) NULL AFTER id,
    ADD COLUMN property_id BINARY(16) NULL AFTER organization_id,
    ADD COLUMN audience_type VARCHAR(30) NOT NULL DEFAULT 'ALL_PROPERTIES' AFTER category,
    ADD COLUMN publish_at DATETIME(6) NULL AFTER pinned,
    ADD COLUMN expires_at DATETIME(6) NULL AFTER publish_at,
    ADD COLUMN attachment_file_id BINARY(16) NULL AFTER expires_at,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 AFTER audit_tracker_id,
    ADD KEY idx_announcements_org_prop_pin (organization_id, property_id, pinned, created_at),
    ADD CONSTRAINT fk_announcements_org FOREIGN KEY (organization_id) REFERENCES organizations (id),
    ADD CONSTRAINT fk_announcements_property FOREIGN KEY (property_id) REFERENCES properties (id),
    ADD CONSTRAINT fk_announcements_file FOREIGN KEY (attachment_file_id) REFERENCES stored_files (id);

UPDATE announcements a
SET
    a.organization_id = COALESCE(
        a.organization_id,
        (SELECT o.id FROM organizations o WHERE o.status <> 9 ORDER BY o.created_at ASC LIMIT 1)
    ),
    a.publish_at = COALESCE(a.publish_at, a.created_at)
WHERE a.organization_id IS NULL;

CREATE TABLE IF NOT EXISTS announcement_reads (
    id BINARY(16) NOT NULL PRIMARY KEY,
    organization_id BINARY(16) NOT NULL,
    announcement_id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    read_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by VARCHAR(100) NULL DEFAULT 'SYSTEM',
    updated_at DATETIME(6) NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    updated_by VARCHAR(100) NULL,
    deleted_at DATETIME(6) NULL,
    deleted_by VARCHAR(100) NULL,
    status SMALLINT NOT NULL DEFAULT 1,
    audit_tracker_id VARCHAR(64) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_ar_org FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT fk_ar_announcement FOREIGN KEY (announcement_id) REFERENCES announcements (id),
    CONSTRAINT fk_ar_user FOREIGN KEY (user_id) REFERENCES users (id),
    UNIQUE KEY uk_announcement_read_user (announcement_id, user_id),
    INDEX idx_ar_user (user_id, read_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. Evolve meals for property-scoped weekly food menu
ALTER TABLE meals
    ADD COLUMN organization_id BINARY(16) NULL AFTER id,
    ADD COLUMN property_id BINARY(16) NULL AFTER organization_id,
    ADD COLUMN day_of_week VARCHAR(20) NULL AFTER date_scheduled,
    ADD COLUMN published BIT(1) NOT NULL DEFAULT b'1' AFTER available,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 AFTER audit_tracker_id,
    ADD KEY idx_meals_prop_date_avail (property_id, date_scheduled, available),
    ADD CONSTRAINT fk_meals_org FOREIGN KEY (organization_id) REFERENCES organizations (id),
    ADD CONSTRAINT fk_meals_property FOREIGN KEY (property_id) REFERENCES properties (id);

UPDATE meals m
SET
    m.organization_id = COALESCE(
        m.organization_id,
        (SELECT o.id FROM organizations o WHERE o.status <> 9 ORDER BY o.created_at ASC LIMIT 1)
    ),
    m.property_id = COALESCE(
        m.property_id,
        (SELECT p.id FROM properties p WHERE p.status <> 9 ORDER BY p.created_at ASC LIMIT 1)
    ),
    m.day_of_week = COALESCE(m.day_of_week, UPPER(DAYNAME(m.date_scheduled)))
WHERE m.organization_id IS NULL;

-- 4. Private documents registry linking stored_files to org, property, tenant, tenancy, or invoice/payment
CREATE TABLE IF NOT EXISTS documents (
    id BINARY(16) NOT NULL PRIMARY KEY,
    organization_id BINARY(16) NOT NULL,
    property_id BINARY(16) NULL,
    stored_file_id BINARY(16) NOT NULL,
    scope_type VARCHAR(30) NOT NULL,
    document_category VARCHAR(40) NOT NULL,
    title VARCHAR(180) NOT NULL,
    description TEXT NULL,
    tenant_id BINARY(16) NULL,
    tenancy_id BINARY(16) NULL,
    invoice_id BINARY(16) NULL,
    payment_id BINARY(16) NULL,
    shared_with_tenant BIT(1) NOT NULL DEFAULT b'0',
    uploaded_by_user_id BINARY(16) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by VARCHAR(100) NULL DEFAULT 'SYSTEM',
    updated_at DATETIME(6) NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    updated_by VARCHAR(100) NULL,
    deleted_at DATETIME(6) NULL,
    deleted_by VARCHAR(100) NULL,
    status SMALLINT NOT NULL DEFAULT 1,
    audit_tracker_id VARCHAR(64) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_docs_org FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT fk_docs_property FOREIGN KEY (property_id) REFERENCES properties (id),
    CONSTRAINT fk_docs_file FOREIGN KEY (stored_file_id) REFERENCES stored_files (id),
    CONSTRAINT fk_docs_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_docs_tenancy FOREIGN KEY (tenancy_id) REFERENCES tenancies (id),
    CONSTRAINT fk_docs_invoice FOREIGN KEY (invoice_id) REFERENCES invoices (id),
    CONSTRAINT fk_docs_payment FOREIGN KEY (payment_id) REFERENCES payments (id),
    CONSTRAINT fk_docs_uploader FOREIGN KEY (uploaded_by_user_id) REFERENCES users (id),
    INDEX idx_docs_org_scope_cat (organization_id, scope_type, document_category),
    INDEX idx_docs_tenant_shared (tenant_id, shared_with_tenant),
    INDEX idx_docs_property_created (property_id, created_at),
    INDEX idx_docs_file (stored_file_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. Manager & staff operational task board
CREATE TABLE IF NOT EXISTS manager_tasks (
    id BINARY(16) NOT NULL PRIMARY KEY,
    organization_id BINARY(16) NOT NULL,
    property_id BINARY(16) NOT NULL,
    assigned_to_user_id BINARY(16) NULL,
    created_by_user_id BINARY(16) NOT NULL,
    complaint_id BINARY(16) NULL,
    title VARCHAR(180) NOT NULL,
    description TEXT NULL,
    priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    task_status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    due_at DATETIME(6) NULL,
    completed_at DATETIME(6) NULL,
    completion_notes TEXT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by VARCHAR(100) NULL DEFAULT 'SYSTEM',
    updated_at DATETIME(6) NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    updated_by VARCHAR(100) NULL,
    deleted_at DATETIME(6) NULL,
    deleted_by VARCHAR(100) NULL,
    status SMALLINT NOT NULL DEFAULT 1,
    audit_tracker_id VARCHAR(64) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_mt_org FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT fk_mt_property FOREIGN KEY (property_id) REFERENCES properties (id),
    CONSTRAINT fk_mt_assignee FOREIGN KEY (assigned_to_user_id) REFERENCES users (id),
    CONSTRAINT fk_mt_creator FOREIGN KEY (created_by_user_id) REFERENCES users (id),
    CONSTRAINT fk_mt_complaint FOREIGN KEY (complaint_id) REFERENCES complaints (id),
    INDEX idx_mt_prop_status_due (property_id, task_status, due_at),
    INDEX idx_mt_assignee_status (assigned_to_user_id, task_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6. Evolve notifications + push tokens, preferences, and external delivery tracking
ALTER TABLE notifications
    ADD COLUMN organization_id BINARY(16) NULL AFTER id,
    ADD COLUMN property_id BINARY(16) NULL AFTER organization_id,
    ADD COLUMN event_type VARCHAR(40) NULL AFTER type,
    ADD COLUMN reference_type VARCHAR(40) NULL AFTER event_type,
    ADD COLUMN reference_id BINARY(16) NULL AFTER reference_type,
    ADD COLUMN read_at DATETIME(6) NULL AFTER is_read,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 AFTER audit_tracker_id,
    ADD KEY idx_notifications_org_prop (organization_id, property_id, created_at),
    ADD CONSTRAINT fk_notifications_org FOREIGN KEY (organization_id) REFERENCES organizations (id),
    ADD CONSTRAINT fk_notifications_property FOREIGN KEY (property_id) REFERENCES properties (id);

UPDATE notifications n
LEFT JOIN organization_members om ON om.user_id = n.user_id AND om.status <> 9
SET n.organization_id = COALESCE(
    n.organization_id,
    om.organization_id,
    (SELECT o.id FROM organizations o WHERE o.status <> 9 ORDER BY o.created_at ASC LIMIT 1)
)
WHERE n.organization_id IS NULL;

CREATE TABLE IF NOT EXISTS push_tokens (
    id BINARY(16) NOT NULL PRIMARY KEY,
    organization_id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    token VARCHAR(255) NOT NULL,
    platform VARCHAR(20) NOT NULL,
    device_name VARCHAR(120) NULL,
    active BIT(1) NOT NULL DEFAULT b'1',
    last_seen_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by VARCHAR(100) NULL DEFAULT 'SYSTEM',
    updated_at DATETIME(6) NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    updated_by VARCHAR(100) NULL,
    deleted_at DATETIME(6) NULL,
    deleted_by VARCHAR(100) NULL,
    status SMALLINT NOT NULL DEFAULT 1,
    audit_tracker_id VARCHAR(64) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_pt_org FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT fk_pt_user FOREIGN KEY (user_id) REFERENCES users (id),
    UNIQUE KEY uk_push_tokens_token (token),
    INDEX idx_pt_user_active (user_id, active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS notification_preferences (
    id BINARY(16) NOT NULL PRIMARY KEY,
    organization_id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    in_app_enabled BIT(1) NOT NULL DEFAULT b'1',
    push_enabled BIT(1) NOT NULL DEFAULT b'1',
    email_enabled BIT(1) NOT NULL DEFAULT b'1',
    sms_enabled BIT(1) NOT NULL DEFAULT b'0',
    whatsapp_enabled BIT(1) NOT NULL DEFAULT b'0',
    digest_quiet_hours VARCHAR(40) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by VARCHAR(100) NULL DEFAULT 'SYSTEM',
    updated_at DATETIME(6) NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    updated_by VARCHAR(100) NULL,
    deleted_at DATETIME(6) NULL,
    deleted_by VARCHAR(100) NULL,
    status SMALLINT NOT NULL DEFAULT 1,
    audit_tracker_id VARCHAR(64) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_np_org FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT fk_np_user FOREIGN KEY (user_id) REFERENCES users (id),
    UNIQUE KEY uk_notif_pref_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS notification_deliveries (
    id BINARY(16) NOT NULL PRIMARY KEY,
    organization_id BINARY(16) NOT NULL,
    notification_id BINARY(16) NULL,
    user_id BINARY(16) NOT NULL,
    channel VARCHAR(20) NOT NULL,
    delivery_status VARCHAR(30) NOT NULL,
    provider_name VARCHAR(60) NOT NULL,
    provider_message_id VARCHAR(150) NULL,
    error_message VARCHAR(500) NULL,
    attempted_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by VARCHAR(100) NULL DEFAULT 'SYSTEM',
    updated_at DATETIME(6) NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    updated_by VARCHAR(100) NULL,
    deleted_at DATETIME(6) NULL,
    deleted_by VARCHAR(100) NULL,
    status SMALLINT NOT NULL DEFAULT 1,
    audit_tracker_id VARCHAR(64) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_nd_org FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT fk_nd_notif FOREIGN KEY (notification_id) REFERENCES notifications (id),
    CONSTRAINT fk_nd_user FOREIGN KEY (user_id) REFERENCES users (id),
    INDEX idx_nd_notif_channel (notification_id, channel),
    INDEX idx_nd_user_status (user_id, delivery_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

