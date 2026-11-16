package com.upcraft.stay.reporting.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.upcraft.stay.reporting.enums.ShareResourceType;

/**
 * Public read-only payload served for a valid, non-expired, non-revoked share token.
 * {@code PUBLIC_VACANCY} strictly omits all tenant/financial PII.
 */
public record PublicShareViewResponseDTO(
        ShareResourceType resourceType,
        String organizationName,
        String propertyName,
        String title,
        String subtitle,
        Instant expiresAt,
        boolean pdfSupported,
        Map<String, String> metadata,
        List<Map<String, Object>> lineItems,
        Map<String, BigDecimal> totals
) {
}

