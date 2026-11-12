package com.upcraft.stay.operations.dto;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import com.upcraft.stay.operations.enums.NotificationEventType;

/**
 * Versioned v1 notification inbox item DTO.
 */
public record NotificationV1ResponseDTO(
        UUID id,
        UUID propertyId,
        String title,
        String message,
        String type,
        NotificationEventType eventType,
        String referenceType,
        UUID referenceId,
        boolean read,
        Instant readAt,
        LocalDateTime createdAt
) {
}

