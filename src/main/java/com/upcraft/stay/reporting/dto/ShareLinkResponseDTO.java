package com.upcraft.stay.reporting.dto;

import java.time.Instant;
import java.util.UUID;

import com.upcraft.stay.reporting.enums.ShareResourceType;

/**
 * Share link response DTO. {@code shareUrl} and {@code whatsappShareUrl} contain the raw token only at creation time.
 */
public record ShareLinkResponseDTO(
        UUID id,
        ShareResourceType resourceType,
        UUID resourceId,
        UUID propertyId,
        String propertyName,
        String shareUrl,
        String whatsappShareUrl,
        Instant expiresAt,
        Instant revokedAt,
        long viewCount,
        boolean active,
        Instant createdAt
) {
}

