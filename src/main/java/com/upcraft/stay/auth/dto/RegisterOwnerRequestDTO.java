package com.upcraft.stay.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.upcraft.stay.common.enums.PropertyType;

/**
 * Request payload for onboarding a new PG owner, organization, and initial property.
 */
public record RegisterOwnerRequestDTO(
        @NotBlank(message = "Full name is required")
        @Size(max = 120, message = "Full name must not exceed 120 characters")
        String name,

        @NotBlank(message = "Email is required")
        @Email(message = "Valid email address is required")
        String email,

        @NotBlank(message = "Phone number is required")
        @Pattern(regexp = "^(\\+91[-\\s]?)?[6-9]\\d{9}$", message = "Enter a valid Indian mobile number")
        String phone,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
        String password,

        @NotBlank(message = "Organization name is required")
        @Size(max = 150, message = "Organization name must not exceed 150 characters")
        String organizationName,

        @NotBlank(message = "Property name is required")
        @Size(max = 150, message = "Property name must not exceed 150 characters")
        String propertyName,

        PropertyType propertyType,

        @NotBlank(message = "Address is required")
        @Size(max = 255, message = "Address must not exceed 255 characters")
        String addressLine,

        @Size(max = 150, message = "Landmark must not exceed 150 characters")
        String landmark,

        @NotBlank(message = "City is required")
        @Size(max = 100, message = "City must not exceed 100 characters")
        String city,

        @NotBlank(message = "State is required")
        @Size(max = 100, message = "State must not exceed 100 characters")
        String state,

        @NotBlank(message = "PIN code is required")
        @Pattern(regexp = "^[1-9][0-9]{5}$", message = "Enter a valid 6-digit Indian PIN code")
        String pinCode
) {}

