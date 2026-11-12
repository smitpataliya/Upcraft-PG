package com.upcraft.stay.tenant.dto;

import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for creating a property-scoped tenant directory record.
 */
public record CreateTenantRequestDTO(
        @NotNull(message = "Property ID is required")
        UUID propertyId,
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
        String notes
) {}

