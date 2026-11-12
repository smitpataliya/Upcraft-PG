package com.upcraft.stay.electricity.dto;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.upcraft.stay.common.enums.MeterScope;
import com.upcraft.stay.common.enums.SplitMethod;

/**
 * Request payload to install and configure a room, bed, or shared electricity meter.
 */
public record CreateMeterRequestDTO(
        @NotNull(message = "Property ID is required")
        UUID propertyId,

        UUID roomId,
        UUID bedId,

        @NotBlank(message = "Meter number is required")
        @Size(max = 60, message = "Meter number must not exceed 60 characters")
        String meterNumber,

        @Size(max = 120, message = "Meter label must not exceed 120 characters")
        String meterLabel,

        @NotNull(message = "Meter scope is required")
        MeterScope meterScope,

        @DecimalMin(value = "0.0", message = "Unit rate cannot be negative")
        BigDecimal unitRate,

        @DecimalMin(value = "0.0", message = "Fixed charge cannot be negative")
        BigDecimal fixedCharge,

        @NotNull(message = "Split method is required")
        SplitMethod splitMethod,

        @DecimalMin(value = "0.0", message = "Initial reading cannot be negative")
        BigDecimal initialReading,

        String notes
) {}

