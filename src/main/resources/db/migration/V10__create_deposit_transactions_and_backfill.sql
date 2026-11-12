-- V10: Create security deposit ledger table separate from rent revenue
-- and idempotently backfill existing tenancy deposits/deductions/refunds.

CREATE TABLE deposit_transactions (
    id BINARY(16) NOT NULL,
    organization_id BINARY(16) NOT NULL,
    property_id BINARY(16) NOT NULL,
    tenant_id BINARY(16) NOT NULL,
    tenancy_id BINARY(16) NOT NULL,
    transaction_type VARCHAR(30) NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    transaction_date DATE NOT NULL,
    payment_method VARCHAR(30) NOT NULL DEFAULT 'OTHER',
    reference_number VARCHAR(120) NULL,
    reason TEXT NULL,
    recorded_by_user_id BINARY(16) NULL,
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
    KEY idx_deposit_tx_tenancy_date (tenancy_id, transaction_date),
    KEY idx_deposit_tx_prop (property_id, transaction_type),
    KEY idx_deposit_tx_tenant (tenant_id, transaction_date),
    CONSTRAINT fk_deposit_tx_org FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT fk_deposit_tx_property FOREIGN KEY (property_id) REFERENCES properties (id),
    CONSTRAINT fk_deposit_tx_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_deposit_tx_tenancy FOREIGN KEY (tenancy_id) REFERENCES tenancies (id),
    CONSTRAINT fk_deposit_tx_actor FOREIGN KEY (recorded_by_user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO deposit_transactions (
    id, organization_id, property_id, tenant_id, tenancy_id,
    transaction_type, amount, transaction_date, payment_method, reason,
    version, created_at, created_by, status, audit_tracker_id
)
SELECT
    UNHEX(REPLACE(UUID(), '-', '')),
    tn.organization_id,
    tn.property_id,
    tn.tenant_id,
    tn.id,
    'COLLECTED',
    tn.deposit_received_amount,
    tn.move_in_date,
    'OTHER',
    'Backfilled initial security deposit received',
    0,
    UTC_TIMESTAMP(6),
    'SYSTEM_MIGRATION_V10',
    1,
    'V10_BACKFILL'
FROM tenancies tn
WHERE tn.status <> 9
  AND tn.deposit_received_amount > 0.00
  AND NOT EXISTS (
      SELECT 1 FROM deposit_transactions dt
      WHERE dt.tenancy_id = tn.id AND dt.transaction_type = 'COLLECTED' AND dt.status <> 9
  );

INSERT INTO deposit_transactions (
    id, organization_id, property_id, tenant_id, tenancy_id,
    transaction_type, amount, transaction_date, payment_method, reason,
    version, created_at, created_by, status, audit_tracker_id
)
SELECT
    UNHEX(REPLACE(UUID(), '-', '')),
    tn.organization_id,
    tn.property_id,
    tn.tenant_id,
    tn.id,
    'DEDUCTION',
    tn.deductions_amount,
    COALESCE(tn.actual_move_out_date, tn.move_in_date),
    'OTHER',
    COALESCE(tn.deduction_reason, 'Backfilled move-out deposit deduction'),
    0,
    UTC_TIMESTAMP(6),
    'SYSTEM_MIGRATION_V10',
    1,
    'V10_BACKFILL'
FROM tenancies tn
WHERE tn.status <> 9
  AND tn.tenancy_status = 'COMPLETED'
  AND tn.deductions_amount > 0.00
  AND NOT EXISTS (
      SELECT 1 FROM deposit_transactions dt
      WHERE dt.tenancy_id = tn.id AND dt.transaction_type = 'DEDUCTION' AND dt.status <> 9
  );

INSERT INTO deposit_transactions (
    id, organization_id, property_id, tenant_id, tenancy_id,
    transaction_type, amount, transaction_date, payment_method, reason,
    version, created_at, created_by, status, audit_tracker_id
)
SELECT
    UNHEX(REPLACE(UUID(), '-', '')),
    tn.organization_id,
    tn.property_id,
    tn.tenant_id,
    tn.id,
    'REFUND',
    tn.refundable_deposit_amount,
    COALESCE(tn.actual_move_out_date, tn.move_in_date),
    'OTHER',
    'Backfilled move-out deposit refund',
    0,
    UTC_TIMESTAMP(6),
    'SYSTEM_MIGRATION_V10',
    1,
    'V10_BACKFILL'
FROM tenancies tn
WHERE tn.status <> 9
  AND tn.tenancy_status = 'COMPLETED'
  AND tn.refund_status IN ('PARTIAL', 'REFUNDED')
  AND tn.refundable_deposit_amount > 0.00
  AND NOT EXISTS (
      SELECT 1 FROM deposit_transactions dt
      WHERE dt.tenancy_id = tn.id AND dt.transaction_type = 'REFUND' AND dt.status <> 9
  );

