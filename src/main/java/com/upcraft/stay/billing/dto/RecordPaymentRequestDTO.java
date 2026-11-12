package com.upcraft.stay.billing.dto;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.upcraft.stay.common.enums.PaymentMethod;

/**
 * Request payload to record a full or partial payment against an invoice.
 */
public record RecordPaymentRequestDTO(
        @NotNull(message = "Payment amount is required")
        @DecimalMin(value = "0.01", message = "Payment amount must be greater than zero")
        BigDecimal amount,

        Instant paidAt,

        @NotNull(message = "Payment method is required")
        PaymentMethod paymentMethod,

        @Size(max = 120, message = "Reference number cannot exceed 120 characters")
        String referenceNumber,

        @Size(max = 100, message = "Idempotency key cannot exceed 100 characters")
        String idempotencyKey,

        @Size(max = 500, message = "Proof URL cannot exceed 500 characters")
        String proofUrl,

        @Size(max = 1000, message = "Notes cannot exceed 1000 characters")
        String notes
) {}

