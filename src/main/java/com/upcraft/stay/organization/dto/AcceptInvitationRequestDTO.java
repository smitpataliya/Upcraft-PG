package com.upcraft.stay.organization.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request payload to accept a manager or tenant invitation by token or one-time code.
 */
public record AcceptInvitationRequestDTO(
        String token,
        String code,

        @NotBlank(message = "Full name is required")
        @Size(max = 120, message = "Full name must not exceed 120 characters")
        String name,

        @Pattern(regexp = "^$|^(\\+91[-\\s]?)?[6-9]\\d{9}$", message = "Enter a valid Indian mobile number")
        String phone,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 100, message = "Password must not be shorter than 8 characters")
        String password
) {}

