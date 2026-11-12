package com.upcraft.stay.operations.dto;

import jakarta.validation.constraints.Size;

/**
 * Request payload updating per-user notification channel opt-in settings.
 */
public record UpdateNotificationPreferenceRequestDTO(
        boolean inAppEnabled,
        boolean pushEnabled,
        boolean emailEnabled,
        boolean smsEnabled,
        boolean whatsappEnabled,
        @Size(max = 40, message = "Quiet hours cannot exceed 40 characters")
        String digestQuietHours
) {
}

