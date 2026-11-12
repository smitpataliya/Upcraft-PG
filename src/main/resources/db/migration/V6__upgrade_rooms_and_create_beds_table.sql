-- V6: Upgrade legacy rooms for multi-property/floor scoping & DECIMAL pricing, and create beds inventory.

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

