package com.upcraft.stay.tenant.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO to transfer an active tenancy from its current bed to another vacant bed.
 */
public record TransferBedRequestDTO(
        @NotNull(message = "Target bed ID is required")
        UUID targetBedId,
        @NotNull(message = "Transfer effective date is required")
        LocalDate transferDate,
        @DecimalMin(value = "0.00", message = "Monthly rent cannot be negative")
        BigDecimal newMonthlyRent,
        String notes
) {}

