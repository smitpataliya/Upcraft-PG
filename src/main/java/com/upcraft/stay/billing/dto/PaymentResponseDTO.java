package com.upcraft.stay.billing.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.upcraft.stay.common.enums.PaymentMethod;
import com.upcraft.stay.common.enums.PaymentStatus;

/**
 * Response projection for a recorded or reversed payment transaction.
 */
public record PaymentResponseDTO(
        UUID id,
        UUID propertyId,
        String propertyName,
        UUID tenantId,
        String tenantName,
        UUID invoiceId,
        String invoiceNumber,
        String receiptNumber,
        BigDecimal amount,
        Instant paidAt,
        PaymentMethod paymentMethod,
        PaymentStatus paymentStatus,
        String referenceNumber,
        String gatewayProvider,
        String gatewayPaymentId,
        String recordedByName,
        String proofUrl,
        String notes,
        Instant createdAt
) {}

