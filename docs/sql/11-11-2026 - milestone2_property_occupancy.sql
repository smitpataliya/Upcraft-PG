-- Upcraft PG — Milestone 2: Property & Occupancy SQL Reference (V5 through V8)
-- Mirrors Flyway migrations V5, V6, V7, and V8 for MySQL 8+.

-- ============================================================================
-- V5: Floors table
-- ============================================================================
CREATE TABLE floors (
    id BINARY(16) NOT NULL,
    organization_id BINARY(16) NOT NULL,
    property_id BINARY(16) NOT NULL,
    floor_number INT NOT NULL,
    name VARCHAR(100) NOT NULL,
    notes TEXT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    created_by VARCHAR(100) NULL,
    updated_at DATETIME(6) NULL,
    updated_by VARCHAR(100) NULL,
    deleted_at DATETIME(6) NULL,
    deleted_by VARCHAR(100) NULL,
    status SMALLINT NOT NULL DEFAULT 1,
    audit_tracker_id VARCHAR(64) NULL,
    active_guard TINYINT GENERATED ALWAYS AS (CASE WHEN status <> 9 THEN 1 ELSE NULL END) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_floors_prop_number_active (property_id, floor_number, active_guard),
    UNIQUE KEY uk_floors_prop_name_active (property_id, name, active_guard),
    KEY idx_floors_org_id (organization_id),
    KEY idx_floors_prop_status (property_id, status),
    CONSTRAINT fk_floors_org FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT fk_floors_property FOREIGN KEY (property_id) REFERENCES properties (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================================
-- V6: Upgrade rooms & create beds table
-- ============================================================================
SET @legacy_room_uk := (
    SELECT INDEX_NAME
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'rooms'
      AND COLUMN_NAME = 'number'
      AND NON_UNIQUE = 0
      AND INDEX_NAME <> 'PRIMARY'
      AND INDEX_NAME <> 'uk_rooms_property_number_active'
    LIMIT 1
);
SET @drop_room_uk_sql := IF(
    @legacy_room_uk IS NOT NULL,
    CONCAT('ALTER TABLE rooms DROP INDEX `', @legacy_room_uk, '`'),
    'SELECT 1'
);
PREPARE drop_room_uk_stmt FROM @drop_room_uk_sql;
EXECUTE drop_room_uk_stmt;
DEALLOCATE PREPARE drop_room_uk_stmt;

ALTER TABLE rooms
    ADD COLUMN property_id BINARY(16) NULL,
    ADD COLUMN floor_id BINARY(16) NULL,
    ADD COLUMN room_type VARCHAR(40) NOT NULL DEFAULT 'SHARING',
    ADD COLUMN base_monthly_rent DECIMAL(12,2) NULL,
    ADD COLUMN default_deposit DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    ADD COLUMN notes TEXT NULL,
    ADD COLUMN room_status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN active_guard TINYINT GENERATED ALWAYS AS (CASE WHEN record_status <> 9 THEN 1 ELSE NULL END) STORED,
    ADD UNIQUE KEY uk_rooms_property_number_active (property_id, number, active_guard),
    ADD KEY idx_rooms_property_id (property_id),
    ADD KEY idx_rooms_floor_id (floor_id),
    ADD CONSTRAINT fk_rooms_property FOREIGN KEY (property_id) REFERENCES properties (id),
    ADD CONSTRAINT fk_rooms_floor FOREIGN KEY (floor_id) REFERENCES floors (id);

CREATE TABLE beds (
    id BINARY(16) NOT NULL,
    organization_id BINARY(16) NOT NULL,
    property_id BINARY(16) NOT NULL,
    room_id BINARY(16) NOT NULL,
    bed_code VARCHAR(60) NOT NULL,
    monthly_rent DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    default_deposit DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    bed_status VARCHAR(30) NOT NULL DEFAULT 'VACANT',
    available_from DATE NULL,
    reserved_until DATETIME(6) NULL,
    notes TEXT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    created_by VARCHAR(100) NULL,
    updated_at DATETIME(6) NULL,
    updated_by VARCHAR(100) NULL,
    deleted_at DATETIME(6) NULL,
    deleted_by VARCHAR(100) NULL,
    status SMALLINT NOT NULL DEFAULT 1,
    audit_tracker_id VARCHAR(64) NULL,
    active_guard TINYINT GENERATED ALWAYS AS (CASE WHEN status <> 9 THEN 1 ELSE NULL END) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_beds_room_code_active (room_id, bed_code, active_guard),
    KEY idx_beds_org_id (organization_id),
    KEY idx_beds_property_status (property_id, bed_status),
    KEY idx_beds_available_from (available_from),
    CONSTRAINT fk_beds_org FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT fk_beds_property FOREIGN KEY (property_id) REFERENCES properties (id),
    CONSTRAINT fk_beds_room FOREIGN KEY (room_id) REFERENCES rooms (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================================
-- V7: Tenants and Tenancies with active_bed_guard uniqueness
-- ============================================================================
CREATE TABLE tenants (
    id BINARY(16) NOT NULL,
    organization_id BINARY(16) NOT NULL,
    property_id BINARY(16) NOT NULL,
    user_id BINARY(16) NULL,
    full_name VARCHAR(150) NOT NULL,
    phone VARCHAR(30) NOT NULL,
    email VARCHAR(255) NULL,
    emergency_contact VARCHAR(150) NULL,
    emergency_phone VARCHAR(30) NULL,
    address VARCHAR(500) NULL,
    notes TEXT NULL,
    tenant_status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
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
    KEY idx_tenants_org_prop_status (organization_id, property_id, tenant_status),
    KEY idx_tenants_user_id (user_id),
    KEY idx_tenants_phone (phone),
    KEY idx_tenants_email (email),
    CONSTRAINT fk_tenants_org FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT fk_tenants_property FOREIGN KEY (property_id) REFERENCES properties (id),
    CONSTRAINT fk_tenants_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE tenancies (
    id BINARY(16) NOT NULL,
    organization_id BINARY(16) NOT NULL,
    property_id BINARY(16) NOT NULL,
    tenant_id BINARY(16) NOT NULL,
    room_id BINARY(16) NOT NULL,
    bed_id BINARY(16) NOT NULL,
    tenancy_status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    move_in_date DATE NOT NULL,
    expected_move_out_date DATE NULL,
    actual_move_out_date DATE NULL,
    agreed_monthly_rent DECIMAL(12,2) NOT NULL,
    security_deposit_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    deposit_received_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    rent_due_day INT NOT NULL DEFAULT 5,
    initial_meter_reading DECIMAL(12,2) NULL,
    final_meter_reading DECIMAL(12,2) NULL,
    unpaid_dues_settled DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    deductions_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    deduction_reason TEXT NULL,
    refundable_deposit_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    refund_status VARCHAR(30) NOT NULL DEFAULT 'NONE',
    agreement_start_date DATE NULL,
    agreement_end_date DATE NULL,
    move_in_notes TEXT NULL,
    move_out_notes TEXT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    created_by VARCHAR(100) NULL,
    updated_at DATETIME(6) NULL,
    updated_by VARCHAR(100) NULL,
    deleted_at DATETIME(6) NULL,
    deleted_by VARCHAR(100) NULL,
    status SMALLINT NOT NULL DEFAULT 1,
    audit_tracker_id VARCHAR(64) NULL,
    active_bed_guard BINARY(16) GENERATED ALWAYS AS (CASE WHEN tenancy_status = 'ACTIVE' AND status <> 9 THEN bed_id ELSE NULL END) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenancies_active_bed (active_bed_guard),
    KEY idx_tenancies_prop_status (property_id, tenancy_status),
    KEY idx_tenancies_tenant_status (tenant_id, tenancy_status),
    KEY idx_tenancies_bed_status (bed_id, tenancy_status),
    CONSTRAINT fk_tenancies_org FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT fk_tenancies_property FOREIGN KEY (property_id) REFERENCES properties (id),
    CONSTRAINT fk_tenancies_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_tenancies_room FOREIGN KEY (room_id) REFERENCES rooms (id),
    CONSTRAINT fk_tenancies_bed FOREIGN KEY (bed_id) REFERENCES beds (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================================
-- V8: Backfill legacy rooms -> beds & legacy members -> tenants/tenancies
-- ============================================================================
UPDATE rooms r
JOIN (
    SELECT id FROM properties WHERE status <> 9 ORDER BY created_at ASC, id ASC LIMIT 1
) default_prop ON 1 = 1
SET
    r.property_id = COALESCE(r.property_id, default_prop.id),
    r.base_monthly_rent = COALESCE(r.base_monthly_rent, CAST(r.price AS DECIMAL(12,2)))
WHERE r.property_id IS NULL
   OR r.base_monthly_rent IS NULL;

INSERT INTO beds (
    id, organization_id, property_id, room_id, bed_code,
    monthly_rent, default_deposit, bed_status,
    version, created_at, created_by, status, audit_tracker_id
)
SELECT
    UNHEX(REPLACE(UUID(), '-', '')),
    p.organization_id,
    r.property_id,
    r.id,
    CONCAT('BED-', seq.n),
    COALESCE(r.base_monthly_rent, CAST(r.price AS DECIMAL(12,2)), 0.00),
    COALESCE(r.default_deposit, 0.00),
    CASE WHEN seq.n <= COALESCE(r.occupied, 0) THEN 'OCCUPIED' ELSE 'VACANT' END,
    0,
    UTC_TIMESTAMP(6),
    'SYSTEM_MIGRATION_V8',
    1,
    'V8_BACKFILL'
FROM rooms r
JOIN properties p ON p.id = r.property_id
JOIN (
    SELECT 1 AS n UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5
    UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9 UNION ALL SELECT 10
    UNION ALL SELECT 11 UNION ALL SELECT 12 UNION ALL SELECT 13 UNION ALL SELECT 14 UNION ALL SELECT 15
    UNION ALL SELECT 16 UNION ALL SELECT 17 UNION ALL SELECT 18 UNION ALL SELECT 19 UNION ALL SELECT 20
) seq ON seq.n <= GREATEST(COALESCE(r.capacity, 1), 1)
WHERE r.record_status <> 9
  AND NOT EXISTS (
      SELECT 1 FROM beds b WHERE b.room_id = r.id AND b.status <> 9
  );
