package com.upcraft.stay.operations.dto;

import java.time.Instant;
import java.util.UUID;

import com.upcraft.stay.operations.enums.NotificationChannel;
import com.upcraft.stay.operations.enums.NotificationDeliveryStatus;

/**
 * Outbound provider notification attempt log DTO.
 */
public record NotificationDeliveryResponseDTO(
        UUID id,
        NotificationChannel channel,
        NotificationDeliveryStatus deliveryStatus,
        String providerName,
        String providerMessageId,
        String errorMessage,
        Instant attemptedAt
) {
}

