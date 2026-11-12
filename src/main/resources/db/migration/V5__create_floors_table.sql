-- V5: Property floor hierarchy with active-record uniqueness.

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

