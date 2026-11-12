package com.upcraft.stay.billing.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.upcraft.stay.common.enums.InvoiceStatus;
import com.upcraft.stay.common.enums.InvoiceType;

/**
 * Paginated invoice summary DTO for owner, manager, and resident billing views.
 */
public record InvoiceSummaryResponseDTO(
        UUID id,
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
        boolean overdue,
        Instant createdAt
) {}

