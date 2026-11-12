package com.upcraft.stay.common.constants;

/**
 * Application-wide domain, localization, HTTP, and audit constants.
 */
public final class AppConstants {

    public static final String DEFAULT_TIMEZONE = "Asia/Kolkata";
    public static final String DEFAULT_CURRENCY = "INR";
    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String TRACE_ID_MDC_KEY = "traceId";
    public static final String ROLE_PREFIX = "ROLE_";
    public static final String SYSTEM_ACTOR = "SYSTEM";

    public static final short STATUS_INACTIVE = 0;
    public static final short STATUS_ACTIVE = 1;
    public static final short STATUS_DELETED = 9;

    public static final int DEFAULT_RENT_DUE_DAY = 5;
    public static final int DEFAULT_GRACE_PERIOD_DAYS = 3;

    private AppConstants() {
    }
}

