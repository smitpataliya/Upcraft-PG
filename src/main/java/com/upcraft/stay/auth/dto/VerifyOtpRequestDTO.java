package com.upcraft.stay.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Request to validate a password-reset OTP code.
 */
public record VerifyOtpRequestDTO(
        @NotBlank(message = "Email is required")
        @Email(message = "Valid email address is required")
        String email,

        @NotBlank(message = "OTP is required")
        String otp
) {}

