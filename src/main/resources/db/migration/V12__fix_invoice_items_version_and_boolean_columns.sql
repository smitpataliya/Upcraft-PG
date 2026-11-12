-- ============================================================================
-- V12: Add missing version columns to invoice_items and payment_adjustments
-- and align meter_readings boolean columns with Hibernate MySQL BIT(1) mapping.
-- Author: Upcraft Stay Platform
-- Date: 12-11-2026
-- ============================================================================

ALTER TABLE invoice_items
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 AFTER audit_tracker_id;

ALTER TABLE payment_adjustments
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 AFTER audit_tracker_id;

ALTER TABLE meter_readings
    MODIFY COLUMN unusual_usage BIT(1) NOT NULL DEFAULT b'0',
    MODIFY COLUMN meter_reset_applied BIT(1) NOT NULL DEFAULT b'0';

