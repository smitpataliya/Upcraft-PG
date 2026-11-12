-- ============================================================================
-- Upcraft PG — V12 Hibernate Schema Validation Alignment
-- Matches Flyway V12__fix_invoice_items_version_and_boolean_columns.sql
-- Date: 12-11-2026
-- ============================================================================

ALTER TABLE invoice_items
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 AFTER audit_tracker_id;

ALTER TABLE payment_adjustments
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 AFTER audit_tracker_id;

ALTER TABLE meter_readings
    MODIFY COLUMN unusual_usage BIT(1) NOT NULL DEFAULT b'0',
    MODIFY COLUMN meter_reset_applied BIT(1) NOT NULL DEFAULT b'0';

