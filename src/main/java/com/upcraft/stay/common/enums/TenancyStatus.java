package com.upcraft.stay.common.enums;

/**
 * Tenancy allocation lifecycle state for a tenant occupying a specific bed.
 */
public enum TenancyStatus {
    RESERVED,
    ACTIVE,
    NOTICE_PERIOD,
    COMPLETED,
    CANCELLED
}

