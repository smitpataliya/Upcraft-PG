package com.upcraft.stay.billing.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request payload to issue an itemized custom or one-time charge invoice for a tenancy.
 */
public record CreateCustomInvoiceRequestDTO(
        @NotNull(message = "Tenancy ID is required")
        UUID tenancyId,

        @Min(value = 2020, message = "Billing year must be 2020 or later")
        @Max(value = 2100, message = "Billing year must be 2100 or earlier")
        Integer billingYear,

        @Min(value = 1, message = "Billing month must be 1 to 12")
        @Max(value = 12, message = "Billing month must be 1 to 12")
        Integer billingMonth,

        LocalDate dueDate,

        @Size(max = 1000, message = "Notes cannot exceed 1000 characters")
        String notes,

        @NotEmpty(message = "At least one invoice line item is required")
        @Valid
        List<CustomInvoiceLineItemRequestDTO> items
) {}

