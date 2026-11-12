package com.upcraft.stay.billing.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.upcraft.stay.common.enums.InvoiceItemType;

/**
 * Single invoice line item definition for custom invoices and one-time charges.
 */
public record CustomInvoiceLineItemRequestDTO(
        @NotNull(message = "Line item type is required")
        InvoiceItemType itemType,

        @NotBlank(message = "Item description is required")
        @Size(max = 255, message = "Description cannot exceed 255 characters")
        String description,

        @DecimalMin(value = "0.01", message = "Quantity must be positive")
        BigDecimal quantity,

        @NotNull(message = "Unit amount is required")
        @DecimalMin(value = "0.00", message = "Unit amount cannot be negative")
        BigDecimal unitAmount
) {}

