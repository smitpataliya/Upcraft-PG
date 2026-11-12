-- V8: Idempotent backfill binding legacy rooms to default property, generating bed inventory,
-- and creating tenant + active tenancy rows for existing assigned PG_MEMBER users.

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

INSERT INTO tenants (
    id, organization_id, property_id, user_id,
    full_name, phone, email, emergency_contact, address,
    tenant_status, version, created_at, created_by, status, audit_tracker_id
)
SELECT
    UNHEX(REPLACE(UUID(), '-', '')),
    p.organization_id,
    r.property_id,
    u.id,
    LEFT(u.name, 150),
    LEFT(COALESCE(NULLIF(u.phone, ''), '0000000000'), 30),
    u.email,
    LEFT(u.emergency_contact, 150),
    LEFT(u.address, 500),
    CASE WHEN u.active = b'1' THEN 'ACTIVE' ELSE 'MOVED_OUT' END,
    0,
    UTC_TIMESTAMP(6),
    'SYSTEM_MIGRATION_V8',
    1,
    'V8_BACKFILL'
FROM users u
JOIN rooms r ON r.id = u.room_id
JOIN properties p ON p.id = r.property_id
WHERE u.role = 'PG_MEMBER'
  AND NOT EXISTS (
      SELECT 1 FROM tenants t WHERE t.user_id = u.id AND t.status <> 9
  );

INSERT INTO tenancies (
    id, organization_id, property_id, tenant_id, room_id, bed_id,
    tenancy_status, move_in_date, agreed_monthly_rent,
    security_deposit_amount, deposit_received_amount, rent_due_day,
    refundable_deposit_amount, refund_status,
    version, created_at, created_by, status, audit_tracker_id
)
SELECT
    UNHEX(REPLACE(UUID(), '-', '')),
    t.organization_id,
    t.property_id,
    t.id,
    ranked_members.room_id,
    ranked_beds.id,
    'ACTIVE',
    COALESCE(ranked_members.check_in_date, UTC_DATE()),
    ranked_beds.monthly_rent,
    ranked_beds.default_deposit,
    0.00,
    5,
    0.00,
    'NONE',
    0,
    UTC_TIMESTAMP(6),
    'SYSTEM_MIGRATION_V8',
    1,
    'V8_BACKFILL'
FROM (
    SELECT
        u.id AS user_id,
        u.room_id,
        u.check_in_date,
        ROW_NUMBER() OVER (PARTITION BY u.room_id ORDER BY u.created_at ASC, u.id ASC) AS slot_no
    FROM users u
    WHERE u.role = 'PG_MEMBER' AND u.active = b'1' AND u.room_id IS NOT NULL
) ranked_members
JOIN tenants t ON t.user_id = ranked_members.user_id AND t.status <> 9
JOIN (
    SELECT
        b.id,
        b.room_id,
        b.monthly_rent,
        b.default_deposit,
        ROW_NUMBER() OVER (
            PARTITION BY b.room_id
            ORDER BY CASE WHEN b.bed_status = 'OCCUPIED' THEN 0 ELSE 1 END, LENGTH(b.bed_code), b.bed_code ASC, b.id ASC
        ) AS slot_no
    FROM beds b
    WHERE b.status <> 9
) ranked_beds ON ranked_beds.room_id = ranked_members.room_id AND ranked_beds.slot_no = ranked_members.slot_no
WHERE NOT EXISTS (
    SELECT 1 FROM tenancies tn WHERE tn.bed_id = ranked_beds.id AND tn.tenancy_status = 'ACTIVE' AND tn.status <> 9
)
AND NOT EXISTS (
    SELECT 1 FROM tenancies tn WHERE tn.tenant_id = t.id AND tn.tenancy_status = 'ACTIVE' AND tn.status <> 9
);
