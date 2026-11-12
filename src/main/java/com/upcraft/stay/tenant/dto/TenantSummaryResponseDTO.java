package com.upcraft.stay.tenant.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.upcraft.stay.common.enums.TenantStatus;

/**
 * Tenant summary record displayed in owner/manager tenant directories.
 */
public record TenantSummaryResponseDTO(
        UUID id,
        UUID propertyId,
        String propertyName,
        UUID linkedUserId,
        String fullName,
        String phone,
        String email,
        TenantStatus tenantStatus,
        UUID activeTenancyId,
        String activeRoomNumber,
        String activeBedCode,
        LocalDate moveInDate,
        Instant createdAt
) {}

