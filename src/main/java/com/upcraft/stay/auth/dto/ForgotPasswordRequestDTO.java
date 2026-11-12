package com.upcraft.stay.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request to initiate a password-reset OTP email.
 */
public record ForgotPasswordRequestDTO(
        @NotBlank(message = "Email is required")
        @Email(message = "Valid email address is required")
        String email
) {}

