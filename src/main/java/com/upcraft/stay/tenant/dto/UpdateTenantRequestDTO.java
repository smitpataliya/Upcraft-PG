package com.upcraft.stay.tenant.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.upcraft.stay.common.enums.TenantStatus;

/**
 * Request DTO for updating tenant profile contact and status details.
 */
public record UpdateTenantRequestDTO(
        @NotBlank(message = "Full name is required")
        @Size(max = 150)
        String fullName,
        @NotBlank(message = "Phone number is required")
        @Size(max = 30)
        String phone,
        @Email(message = "Invalid email format")
        @Size(max = 255)
        String email,
        @Size(max = 150)
        String emergencyContact,
        @Size(max = 30)
        String emergencyPhone,
        @Size(max = 500)
        String address,
        String notes,
        TenantStatus tenantStatus
) {}

