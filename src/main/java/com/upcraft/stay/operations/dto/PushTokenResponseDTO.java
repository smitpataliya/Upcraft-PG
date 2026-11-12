package com.upcraft.stay.operations.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Registered mobile push token response DTO.
 */
public record PushTokenResponseDTO(
        UUID id,
        String token,
        String platform,
        String deviceName,
        boolean active,
        Instant lastSeenAt
) {
}

