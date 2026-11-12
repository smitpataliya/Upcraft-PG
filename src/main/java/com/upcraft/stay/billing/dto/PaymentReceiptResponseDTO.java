package com.upcraft.stay.billing.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.upcraft.stay.common.enums.PaymentMethod;
import com.upcraft.stay.common.enums.PaymentStatus;

/**
 * Printable and shareable payment receipt details.
 */
public record PaymentReceiptResponseDTO(
        UUID paymentId,
        String receiptNumber,
        String organizationName,
        String propertyName,
        String propertyAddress,
        String tenantName,
        String tenantPhone,
        String roomNumber,
        String bedCode,
        String invoiceNumber,
        Integer billingYear,
        Integer billingMonth,
        Instant paidAt,
        PaymentMethod paymentMethod,
        PaymentStatus paymentStatus,
        String referenceNumber,
        BigDecimal amountPaid,
        BigDecimal invoiceTotal,
        BigDecimal invoicePaidTotal,
        BigDecimal invoiceBalanceRemaining,
        String receivedByName,
        String notes
) {}

