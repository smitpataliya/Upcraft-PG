package com.upcraft.stay.billing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.upcraft.stay.common.enums.InvoiceStatus;

/**
 * Request payload to waive or cancel an open invoice with a mandatory audit reason.
 */
public record UpdateInvoiceStatusRequestDTO(
        @NotNull(message = "Target invoice status is required")
        InvoiceStatus invoiceStatus,

        @NotBlank(message = "Reason is required when cancelling or waiving an invoice")
        @Size(max = 1000, message = "Reason cannot exceed 1000 characters")
        String reason
) {}

