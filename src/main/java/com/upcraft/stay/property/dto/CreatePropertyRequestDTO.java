package com.upcraft.stay.property.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.upcraft.stay.common.enums.LateFeeType;
import com.upcraft.stay.common.enums.PropertyType;

/**
 * Request payload to create a PG property inside the owner's organization.
 */
public record CreatePropertyRequestDTO(
        @NotBlank(message = "Property name is required")
        @Size(max = 150, message = "Property name must not exceed 150 characters")
        String name,

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
        String pinCode,

        String country,

        @Pattern(regexp = "^$|^(\\+91[-\\s]?)?[6-9]\\d{9}$", message = "Enter a valid Indian contact phone")
        String contactPhone,

        @Email(message = "Enter a valid contact email")
        String contactEmail,

        @Min(value = 1, message = "Rent due day must be between 1 and 28")
        @Max(value = 28, message = "Rent due day must be between 1 and 28")
        Integer rentDueDay,

        @Min(value = 0, message = "Grace period cannot be negative")
        @Max(value = 30, message = "Grace period cannot exceed 30 days")
        Integer gracePeriodDays,

        LateFeeType lateFeeType,

        @DecimalMin(value = "0.00", message = "Late fee cannot be negative")
        BigDecimal lateFeeAmount,

        @DecimalMin(value = "0.0000", message = "Electricity rate cannot be negative")
        BigDecimal defaultElectricityRate,

        String checkInRules,
        String checkOutRules
) {}

