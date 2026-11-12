package com.upcraft.stay.common.enums;

/**
 * Cost splitting policy applied when generating resident electricity bills from a reading.
 */
public enum SplitMethod {
    SINGLE_TENANT,
    EQUAL,
    PER_BED,
    PERCENTAGE,
    MANUAL
}

