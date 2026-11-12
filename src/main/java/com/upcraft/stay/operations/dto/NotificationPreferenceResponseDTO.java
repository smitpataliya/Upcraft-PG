package com.upcraft.stay.operations.dto;

/**
 * User notification channel preferences response DTO.
 */
public record NotificationPreferenceResponseDTO(
        boolean inAppEnabled,
        boolean pushEnabled,
        boolean emailEnabled,
        boolean smsEnabled,
        boolean whatsappEnabled,
        String digestQuietHours
) {
}

