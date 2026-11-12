-- Upcraft PG — Milestone 1 Foundation SQL Reference
-- Date: 11-11-2026
-- Matches Flyway migrations V1 through V4 (MySQL 8+)

-- ============================================================================
-- V1: Baseline Existing Schema (Safe IF NOT EXISTS for fresh databases)
-- ============================================================================
CREATE TABLE IF NOT EXISTS rooms (
    id BINARY(16) NOT NULL,
    number VARCHAR(255) NOT NULL,
    floor INT NOT NULL,
    sharing VARCHAR(255) NOT NULL,
    capacity INT NOT NULL,
    occupied INT NOT NULL DEFAULT 0,
    price DOUBLE NOT NULL,
    created_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_rooms_number (number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS room_amenities (
    room_id BINARY(16) NOT NULL,
    amenity VARCHAR(255) NULL,
    KEY idx_room_amenities_room_id (room_id),
    CONSTRAINT fk_room_amenities_room FOREIGN KEY (room_id) REFERENCES rooms (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS users (
    id BINARY(16) NOT NULL,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    username VARCHAR(255) NULL,
    password VARCHAR(255) NULL,
    phone VARCHAR(255) NULL,
    google_id VARCHAR(255) NULL,
    avatar_url VARCHAR(255) NULL,
    role VARCHAR(50) NOT NULL,
    active BIT(1) NOT NULL DEFAULT b'1',
    emergency_contact VARCHAR(255) NULL,
    address VARCHAR(255) NULL,
    bed_number VARCHAR(255) NULL,
    check_in_date DATE NULL,
    room_id BINARY(16) NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email (email),
    UNIQUE KEY uk_users_username (username),
    KEY idx_users_room_id (room_id),
    CONSTRAINT fk_users_room FOREIGN KEY (room_id) REFERENCES rooms (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS meals (
    id BINARY(16) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(255) NULL,
    image_url VARCHAR(255) NULL,
    type VARCHAR(50) NOT NULL,
    serving_time VARCHAR(255) NULL,
    veg BIT(1) NOT NULL DEFAULT b'1',
    available BIT(1) NOT NULL DEFAULT b'1',
    date_scheduled DATE NOT NULL,
    created_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    KEY idx_meals_date_scheduled (date_scheduled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS announcements (
    id BINARY(16) NOT NULL,
    title VARCHAR(255) NOT NULL,
    body TEXT NOT NULL,
    category VARCHAR(255) NULL,
    pinned BIT(1) NOT NULL DEFAULT b'0',
    author VARCHAR(255) NOT NULL,
    created_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    KEY idx_announcements_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS complaints (
    id BINARY(16) NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    category VARCHAR(50) NOT NULL,
    priority VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    member_id BINARY(16) NOT NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    KEY idx_complaints_member_id (member_id),
    KEY idx_complaints_status (status),
    CONSTRAINT fk_complaints_member FOREIGN KEY (member_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS complaint_timeline (
    id BINARY(16) NOT NULL,
    label VARCHAR(255) NOT NULL,
    updated_by VARCHAR(255) NULL,
    at DATETIME(6) NULL,
    complaint_id BINARY(16) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_complaint_timeline_complaint_id (complaint_id),
    CONSTRAINT fk_complaint_timeline_complaint FOREIGN KEY (complaint_id) REFERENCES complaints (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS queries (
    id BINARY(16) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    category VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    member_id BINARY(16) NOT NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    KEY idx_queries_member_id (member_id),
    KEY idx_queries_status (status),
    CONSTRAINT fk_queries_member FOREIGN KEY (member_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS query_replies (
    id BINARY(16) NOT NULL,
    message TEXT NOT NULL,
    sender_name VARCHAR(255) NOT NULL,
    sender_role VARCHAR(50) NOT NULL,
    query_id BINARY(16) NOT NULL,
    at DATETIME(6) NULL,
    PRIMARY KEY (id),
    KEY idx_query_replies_query_id (query_id),
    CONSTRAINT fk_query_replies_query FOREIGN KEY (query_id) REFERENCES queries (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS notifications (
    id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    title VARCHAR(255) NOT NULL,
    message VARCHAR(1000) NOT NULL,
    type VARCHAR(255) NULL,
    is_read BIT(1) NOT NULL DEFAULT b'0',
    created_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    KEY idx_notifications_user_read (user_id, is_read),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================================
-- V3: Organizations, Memberships, Properties, Manager Assignments, Invitations
-- (See src/main/resources/db/migration/V2..V4 for full incremental DDL/DML)
-- ============================================================================
CREATE TABLE organizations (
    id BINARY(16) NOT NULL,
    name VARCHAR(150) NOT NULL,
    slug VARCHAR(160) NOT NULL,
    owner_user_id BINARY(16) NOT NULL,
    contact_email VARCHAR(255) NULL,
    contact_phone VARCHAR(30) NULL,
    timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Kolkata',
    currency_code VARCHAR(10) NOT NULL DEFAULT 'INR',
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    created_by VARCHAR(100) NULL,
    updated_at DATETIME(6) NULL,
    updated_by VARCHAR(100) NULL,
    deleted_at DATETIME(6) NULL,
    deleted_by VARCHAR(100) NULL,
    status SMALLINT NOT NULL DEFAULT 1,
    audit_tracker_id VARCHAR(64) NULL,
    active_guard TINYINT GENERATED ALWAYS AS (IF(status <> 9, 1, NULL)) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_organizations_slug_active (slug, active_guard),
    KEY idx_organizations_owner_user_id (owner_user_id),
    KEY idx_organizations_status (status),
    CONSTRAINT fk_organizations_owner_user FOREIGN KEY (owner_user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE organization_members (
    id BINARY(16) NOT NULL,
    organization_id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    member_role VARCHAR(50) NOT NULL,
    is_primary BIT(1) NOT NULL DEFAULT b'1',
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    created_by VARCHAR(100) NULL,
    updated_at DATETIME(6) NULL,
    updated_by VARCHAR(100) NULL,
    deleted_at DATETIME(6) NULL,
    deleted_by VARCHAR(100) NULL,
    status SMALLINT NOT NULL DEFAULT 1,
    audit_tracker_id VARCHAR(64) NULL,
    active_guard TINYINT GENERATED ALWAYS AS (IF(status <> 9, 1, NULL)) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_org_members_org_user_active (organization_id, user_id, active_guard),
    KEY idx_org_members_user_id (user_id),
    KEY idx_org_members_org_role (organization_id, member_role),
    CONSTRAINT fk_org_members_org FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT fk_org_members_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE properties (
    id BINARY(16) NOT NULL,
    organization_id BINARY(16) NOT NULL,
    name VARCHAR(150) NOT NULL,
    property_type VARCHAR(30) NOT NULL DEFAULT 'PG',
    address_line VARCHAR(255) NOT NULL,
    landmark VARCHAR(150) NULL,
    city VARCHAR(100) NOT NULL,
    state VARCHAR(100) NOT NULL,
    pin_code VARCHAR(12) NOT NULL,
    country VARCHAR(80) NOT NULL DEFAULT 'India',
    contact_phone VARCHAR(30) NULL,
    contact_email VARCHAR(255) NULL,
    rent_due_day INT NOT NULL DEFAULT 5,
    grace_period_days INT NOT NULL DEFAULT 3,
    late_fee_type VARCHAR(30) NOT NULL DEFAULT 'NONE',
    late_fee_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    default_electricity_rate DECIMAL(10,4) NOT NULL DEFAULT 0.0000,
    check_in_rules TEXT NULL,
    check_out_rules TEXT NULL,
    property_status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    created_by VARCHAR(100) NULL,
    updated_at DATETIME(6) NULL,
    updated_by VARCHAR(100) NULL,
    deleted_at DATETIME(6) NULL,
    deleted_by VARCHAR(100) NULL,
    status SMALLINT NOT NULL DEFAULT 1,
    audit_tracker_id VARCHAR(64) NULL,
    active_guard TINYINT GENERATED ALWAYS AS (IF(status <> 9, 1, NULL)) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_properties_org_name_active (organization_id, name, active_guard),
    KEY idx_properties_org_status (organization_id, property_status),
    KEY idx_properties_city (city),
    CONSTRAINT fk_properties_org FOREIGN KEY (organization_id) REFERENCES organizations (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE manager_property_assignments (
    id BINARY(16) NOT NULL,
    organization_id BINARY(16) NOT NULL,
    property_id BINARY(16) NOT NULL,
    manager_user_id BINARY(16) NOT NULL,
    can_manage_tenants BIT(1) NOT NULL DEFAULT b'1',
    can_record_payments BIT(1) NOT NULL DEFAULT b'1',
    can_enter_meters BIT(1) NOT NULL DEFAULT b'1',
    can_handle_complaints BIT(1) NOT NULL DEFAULT b'1',
    can_publish_notices BIT(1) NOT NULL DEFAULT b'1',
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    created_by VARCHAR(100) NULL,
    updated_at DATETIME(6) NULL,
    updated_by VARCHAR(100) NULL,
    deleted_at DATETIME(6) NULL,
    deleted_by VARCHAR(100) NULL,
    status SMALLINT NOT NULL DEFAULT 1,
    audit_tracker_id VARCHAR(64) NULL,
    active_guard TINYINT GENERATED ALWAYS AS (IF(status <> 9, 1, NULL)) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_mgr_prop_active (property_id, manager_user_id, active_guard),
    KEY idx_mgr_prop_manager_user (manager_user_id),
    KEY idx_mgr_prop_org (organization_id),
    CONSTRAINT fk_mgr_prop_org FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT fk_mgr_prop_property FOREIGN KEY (property_id) REFERENCES properties (id),
    CONSTRAINT fk_mgr_prop_user FOREIGN KEY (manager_user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE invitations (
    id BINARY(16) NOT NULL,
    organization_id BINARY(16) NOT NULL,
    property_id BINARY(16) NULL,
    email VARCHAR(255) NOT NULL,
    phone VARCHAR(30) NULL,
    invitee_name VARCHAR(150) NOT NULL,
    target_role VARCHAR(30) NOT NULL,
    token_hash VARCHAR(128) NOT NULL,
    invite_code_hash VARCHAR(128) NOT NULL,
    permissions_json TEXT NULL,
    invitation_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    expires_at DATETIME(6) NOT NULL,
    accepted_at DATETIME(6) NULL,
    invited_by_user_id BINARY(16) NOT NULL,
    accepted_by_user_id BINARY(16) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    created_by VARCHAR(100) NULL,
    updated_at DATETIME(6) NULL,
    updated_by VARCHAR(100) NULL,
    deleted_at DATETIME(6) NULL,
    deleted_by VARCHAR(100) NULL,
    status SMALLINT NOT NULL DEFAULT 1,
    audit_tracker_id VARCHAR(64) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_invitations_token_hash (token_hash),
    UNIQUE KEY uk_invitations_code_hash (invite_code_hash),
    KEY idx_invitations_org_status (organization_id, invitation_status),
    KEY idx_invitations_email_status (email, invitation_status),
    KEY idx_invitations_property_id (property_id),
    CONSTRAINT fk_invitations_org FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT fk_invitations_property FOREIGN KEY (property_id) REFERENCES properties (id),
    CONSTRAINT fk_invitations_invited_by FOREIGN KEY (invited_by_user_id) REFERENCES users (id),
    CONSTRAINT fk_invitations_accepted_by FOREIGN KEY (accepted_by_user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

