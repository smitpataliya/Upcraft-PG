package com.upcraft.stay.electricity.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.upcraft.stay.common.enums.MeterStatus;
import com.upcraft.stay.common.enums.SplitMethod;

/**
 * Request payload to update meter tariff, split method, or operational status without mutating history.
 */
public record UpdateMeterRequestDTO(
        @Size(max = 120, message = "Meter label must not exceed 120 characters")
        String meterLabel,

        @NotNull(message = "Unit rate is required")
        @DecimalMin(value = "0.0", message = "Unit rate cannot be negative")
        BigDecimal unitRate,

        @NotNull(message = "Fixed charge is required")
        @DecimalMin(value = "0.0", message = "Fixed charge cannot be negative")
        BigDecimal fixedCharge,

        @NotNull(message = "Split method is required")
        SplitMethod splitMethod,

        @NotNull(message = "Meter status is required")
        MeterStatus meterStatus,

        String notes
) {}

