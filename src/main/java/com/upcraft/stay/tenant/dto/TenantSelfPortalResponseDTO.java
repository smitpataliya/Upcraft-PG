package com.upcraft.stay.tenant.dto;

import java.util.List;
import java.util.UUID;

/**
 * Tenant self-service portal response exposing only the authenticated resident's own
 * tenancy, room/bed details, and property house rules.
 */
public record TenantSelfPortalResponseDTO(
        UUID userId,
        String fullName,
        String email,
        String phone,
        String checkInRules,
        String checkOutRules,
        TenancyResponseDTO activeTenancy,
        List<TenancyResponseDTO> residencyHistory
) {}

