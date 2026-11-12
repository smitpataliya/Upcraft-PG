package com.upcraft.stay.billing.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.upcraft.stay.common.enums.PaymentAdjustmentType;

/**
 * Audit trail item representing a reversal or adjustment against a payment.
 */
public record PaymentAdjustmentResponseDTO(
        UUID id,
        UUID paymentId,
        String receiptNumber,
        PaymentAdjustmentType adjustmentType,
        BigDecimal amount,
        String reason,
        String adjustedByName,
        Instant adjustedAt
) {}

