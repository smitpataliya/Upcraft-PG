package com.upcraft.stay.billing.dto;

import java.math.BigDecimal;

/**
 * Aggregated property/organization financial overview separating rent revenue from held security deposits.
 */
public record FinancialSummaryResponseDTO(
        BigDecimal totalBilled,
        BigDecimal totalCollected,
        BigDecimal totalOutstanding,
        BigDecimal overdueBalance,
        long overdueInvoiceCount,
        double collectionRatePercent,
        BigDecimal depositHeldTotal,
        BigDecimal depositRefundedTotal
) {}

