package com.upcraft.stay.property.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.upcraft.stay.common.enums.LateFeeType;
import com.upcraft.stay.common.enums.PropertyStatus;
import com.upcraft.stay.common.enums.PropertyType;

/**
 * Request DTO for updating property configuration, rent billing policy, and rules.
 */
public record UpdatePropertyRequestDTO(
        @NotBlank(message = "Property name is required")
        @Size(max = 150, message = "Property name cannot exceed 150 characters")
        String name,
        @NotNull(message = "Property type is required")
        PropertyType propertyType,
        @NotBlank(message = "Address is required")
        @Size(max = 255)
        String addressLine,
        @Size(max = 150)
        String landmark,
        @NotBlank(message = "City is required")
        @Size(max = 100)
        String city,
        @NotBlank(message = "State is required")
        @Size(max = 100)
        String state,
        @NotBlank(message = "PIN code is required")
        @Size(max = 12)
        String pinCode,
        @Size(max = 80)
        String country,
        @Size(max = 30)
        String contactPhone,
        @Size(max = 255)
        String contactEmail,
        @Min(value = 1, message = "Rent due day must be between 1 and 28")
        @Max(value = 28, message = "Rent due day must be between 1 and 28")
        Integer rentDueDay,
        @Min(value = 0)
        @Max(value = 30)
        Integer gracePeriodDays,
        LateFeeType lateFeeType,
        @DecimalMin("0.00")
        BigDecimal lateFeeAmount,
        @DecimalMin("0.0000")
        BigDecimal defaultElectricityRate,
        String checkInRules,
        String checkOutRules,
        PropertyStatus propertyStatus
) {}

