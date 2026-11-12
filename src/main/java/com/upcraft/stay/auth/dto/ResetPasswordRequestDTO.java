package com.upcraft.stay.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request to set a new password using a verified reset token.
 */
public record ResetPasswordRequestDTO(
        @NotBlank(message = "Reset token is required")
        String resetToken,

        @NotBlank(message = "New password is required")
        @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
        String newPassword
) {}

