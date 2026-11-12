package com.upcraft.stay.billing.dto;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request payload for idempotent monthly rent invoice generation.
 */
public record GenerateInvoicesRequestDTO(
        UUID propertyId,

        @NotNull(message = "Billing year is required")
        @Min(value = 2020, message = "Billing year must be 2020 or later")
        @Max(value = 2100, message = "Billing year must be 2100 or earlier")
        Integer billingYear,

        @NotNull(message = "Billing month is required")
        @Min(value = 1, message = "Billing month must be between 1 and 12")
        @Max(value = 12, message = "Billing month must be between 1 and 12")
        Integer billingMonth,

        Boolean prorateMidMonth,

        @DecimalMin(value = "0.00", message = "Discount amount cannot be negative")
        BigDecimal defaultDiscountAmount,

        @Size(max = 500, message = "Notes cannot exceed 500 characters")
        String notes
) {}

