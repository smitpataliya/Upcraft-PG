-- V4: Idempotent backfill creating a default organization and property for any existing PG_OWNER
-- and associating existing PG_OWNER / PG_MEMBER users cleanly without data loss.

INSERT INTO organizations (
    id, name, slug, owner_user_id, contact_email, contact_phone,
    timezone, currency_code, version, created_at, created_by, status, audit_tracker_id
)
SELECT
    UNHEX(REPLACE(UUID(), '-', '')),
    LEFT(CONCAT(u.name, ' PG'), 150),
    CONCAT('org-', LOWER(HEX(u.id))),
    u.id,
    u.email,
    u.phone,
    'Asia/Kolkata',
    'INR',
    0,
    UTC_TIMESTAMP(6),
    'SYSTEM_MIGRATION_V4',
    1,
    'V4_BACKFILL'
FROM users u
WHERE u.role = 'PG_OWNER'
  AND NOT EXISTS (
      SELECT 1 FROM organizations o
      WHERE o.owner_user_id = u.id AND o.status <> 9
  );

INSERT INTO organization_members (
    id, organization_id, user_id, member_role, is_primary,
    version, created_at, created_by, status, audit_tracker_id
)
SELECT
    UNHEX(REPLACE(UUID(), '-', '')),
    o.id,
    o.owner_user_id,
    'PG_OWNER',
    b'1',
    0,
    UTC_TIMESTAMP(6),
    'SYSTEM_MIGRATION_V4',
    1,
    'V4_BACKFILL'
FROM organizations o
WHERE o.status <> 9
  AND NOT EXISTS (
      SELECT 1 FROM organization_members om
      WHERE om.organization_id = o.id AND om.user_id = o.owner_user_id AND om.status <> 9
  );

INSERT INTO properties (
    id, organization_id, name, property_type, address_line, city, state, pin_code,
    country, contact_phone, contact_email, rent_due_day, grace_period_days,
    late_fee_type, late_fee_amount, default_electricity_rate, property_status,
    version, created_at, created_by, status, audit_tracker_id
)
SELECT
    UNHEX(REPLACE(UUID(), '-', '')),
    o.id,
    LEFT(CONCAT(o.name, ' - Main Property'), 150),
    'PG',
    'Primary PG Address',
    'Update City',
    'Update State',
    '000000',
    'India',
    o.contact_phone,
    o.contact_email,
    5,
    3,
    'NONE',
    0.00,
    0.0000,
    'ACTIVE',
    0,
    UTC_TIMESTAMP(6),
    'SYSTEM_MIGRATION_V4',
    1,
    'V4_BACKFILL'
FROM organizations o
WHERE o.status <> 9
  AND NOT EXISTS (
      SELECT 1 FROM properties p
      WHERE p.organization_id = o.id AND p.status <> 9
  );

INSERT INTO organization_members (
    id, organization_id, user_id, member_role, is_primary,
    version, created_at, created_by, status, audit_tracker_id
)
SELECT
    UNHEX(REPLACE(UUID(), '-', '')),
    first_org.id,
    u.id,
    'PG_MEMBER',
    b'1',
    0,
    UTC_TIMESTAMP(6),
    'SYSTEM_MIGRATION_V4',
    1,
    'V4_BACKFILL'
FROM users u
CROSS JOIN (
    SELECT id FROM organizations WHERE status <> 9 ORDER BY created_at ASC, id ASC LIMIT 1
) first_org
WHERE u.role = 'PG_MEMBER'
  AND NOT EXISTS (
      SELECT 1 FROM organization_members om
      WHERE om.user_id = u.id AND om.status <> 9
  );

