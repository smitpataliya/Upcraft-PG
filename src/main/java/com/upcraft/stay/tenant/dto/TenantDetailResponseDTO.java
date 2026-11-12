package com.upcraft.stay.tenant.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.upcraft.stay.common.enums.TenantStatus;

/**
 * Complete tenant profile with active and historical tenancies.
 */
public record TenantDetailResponseDTO(
        UUID id,
        UUID organizationId,
        UUID propertyId,
        String propertyName,
        UUID linkedUserId,
        String fullName,
        String phone,
        String email,
        String emergencyContact,
        String emergencyPhone,
        String address,
        String notes,
        TenantStatus tenantStatus,
        Instant createdAt,
        List<TenancyResponseDTO> tenancies
) {}

