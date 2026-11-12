package com.upcraft.stay.common.enums;

/**
 * Payment record status; reversed payments stay preserved for financial auditability.
 */
public enum PaymentStatus {
    COMPLETED,
    REVERSED,
    PENDING,
    FAILED
}

