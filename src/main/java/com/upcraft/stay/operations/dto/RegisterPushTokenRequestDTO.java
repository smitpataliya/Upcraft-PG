package com.upcraft.stay.operations.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload registering or refreshing an Expo/mobile device push token.
 */
public record RegisterPushTokenRequestDTO(
        @NotBlank(message = "Push token is required")
        @Size(max = 255, message = "Push token cannot exceed 255 characters")
        String token,
        @NotBlank(message = "Platform is required")
        @Size(max = 20, message = "Platform cannot exceed 20 characters")
        String platform,
        @Size(max = 120, message = "Device name cannot exceed 120 characters")
        String deviceName
) {
}

