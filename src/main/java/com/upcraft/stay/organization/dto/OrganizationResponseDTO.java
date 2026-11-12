package com.upcraft.stay.organization.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Organization summary response DTO.
 */
public record OrganizationResponseDTO(
        UUID id,
        String name,
        String slug,
        UUID ownerUserId,
        String contactEmail,
        String contactPhone,
        String timezone,
        String currencyCode,
        Instant createdAt
) {}

