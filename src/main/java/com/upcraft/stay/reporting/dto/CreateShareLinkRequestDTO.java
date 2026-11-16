package com.upcraft.stay.reporting.dto;

import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import com.upcraft.stay.reporting.enums.ShareResourceType;

/**
 * Request payload for creating a revocable expiring share link.
 */
public record CreateShareLinkRequestDTO(
        @NotNull(message = "Resource type is required")
        ShareResourceType resourceType,
        @NotNull(message = "Resource ID is required")
        UUID resourceId,
        @Min(value = 1, message = "Expiry hours must be at least 1")
        @Max(value = 168, message = "Expiry hours cannot exceed 168")
        Integer expiryHours
) {
}

