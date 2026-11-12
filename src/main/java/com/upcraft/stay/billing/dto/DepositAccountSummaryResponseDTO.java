package com.upcraft.stay.billing.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.upcraft.stay.common.dto.PageResponseDTO;
import com.upcraft.stay.common.enums.RefundStatus;

/**
 * Security deposit balance summary and paginated transaction ledger for a tenancy.
 */
public record DepositAccountSummaryResponseDTO(
        UUID tenancyId,
        UUID propertyId,
        String propertyName,
        UUID tenantId,
        String tenantName,
        String roomNumber,
        String bedCode,
        BigDecimal expectedDeposit,
        BigDecimal collectedAmount,
        BigDecimal deductionsAmount,
        BigDecimal refundedAmount,
        BigDecimal forfeitedAmount,
        BigDecimal heldBalance,
        BigDecimal outstandingDeposit,
        RefundStatus refundStatus,
        PageResponseDTO<DepositTransactionResponseDTO> transactions
) {}

