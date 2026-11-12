package com.upcraft.stay.auth.dto;

/**
 * Response returned when an OTP verification succeeds.
 */
public record VerifyOtpResponseDTO(
        String resetToken,
        String message
) {}

