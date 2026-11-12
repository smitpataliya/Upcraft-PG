package com.upcraft.stay.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request payload for email/username and password authentication.
 */
public record LoginRequestDTO(
        @NotBlank(message = "Email or username is required")
        String email,

        @NotBlank(message = "Password is required")
        String password
) {}

