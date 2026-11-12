package com.upcraft.stay.operations.dto;

import java.util.List;
import java.util.UUID;

import com.upcraft.stay.operations.enums.NotificationEventType;

/**
 * Outbound notification dispatch payload passed to channel providers.
 */
public record NotificationDispatchCommand(
        UUID organizationId,
        UUID notificationId,
        UUID recipientUserId,
        String recipientEmail,
        String recipientPhone,
        NotificationEventType eventType,
        String title,
        String message,
        String referenceType,
        UUID referenceId,
        List<String> pushTokens
) {
}

