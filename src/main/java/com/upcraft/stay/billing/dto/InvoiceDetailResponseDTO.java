package com.upcraft.stay.billing.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.upcraft.stay.common.enums.InvoiceStatus;
import com.upcraft.stay.common.enums.InvoiceType;

/**
 * Complete invoice detail including itemized breakdown, payment receipts, and adjustments.
 */
public record InvoiceDetailResponseDTO(
        UUID id,
        UUID organizationId,
        UUID propertyId,
        String propertyName,
        UUID tenantId,
        String tenantName,
        String tenantPhone,
        UUID tenancyId,
        String roomNumber,
        String bedCode,
        String invoiceNumber,
        InvoiceType invoiceType,
        Integer billingYear,
        Integer billingMonth,
        LocalDate periodStart,
        LocalDate periodEnd,
        LocalDate issueDate,
        LocalDate dueDate,
        BigDecimal subtotalAmount,
        BigDecimal discountAmount,
        BigDecimal lateFeeAmount,
        BigDecimal totalAmount,
        BigDecimal paidAmount,
        BigDecimal balanceDue,
        InvoiceStatus invoiceStatus,
        String notes,
        String statusReason,
        Instant createdAt,
        List<InvoiceItemResponseDTO> items,
        List<PaymentResponseDTO> payments,
        List<PaymentAdjustmentResponseDTO> adjustments
) {}

