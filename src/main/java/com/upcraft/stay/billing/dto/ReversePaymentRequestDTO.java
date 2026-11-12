package com.upcraft.stay.billing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload to reverse a completed payment while preserving immutable ledger history.
 */
public record ReversePaymentRequestDTO(
        @NotBlank(message = "Reversal reason is required")
        @Size(max = 1000, message = "Reason cannot exceed 1000 characters")
        String reason
) {}

