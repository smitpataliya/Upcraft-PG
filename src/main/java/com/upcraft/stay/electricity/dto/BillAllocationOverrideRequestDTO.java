package com.upcraft.stay.electricity.dto;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/**
 * Percentage or manual currency override for an individual tenancy inside a shared meter split.
 */
public record BillAllocationOverrideRequestDTO(
        @NotNull(message = "Tenancy ID is required")
        UUID tenancyId,

        @DecimalMin(value = "0.01", message = "Percentage must be positive")
        BigDecimal sharePercentage,

        @DecimalMin(value = "0.00", message = "Manual amount cannot be negative")
        BigDecimal manualAmount
) {}

