package com.upcraft.stay.billing.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.upcraft.stay.common.enums.DepositTransactionType;
import com.upcraft.stay.common.enums.PaymentMethod;

/**
 * Single security deposit movement row.
 */
public record DepositTransactionResponseDTO(
        UUID id,
        UUID tenancyId,
        UUID propertyId,
        UUID tenantId,
        DepositTransactionType transactionType,
        BigDecimal amount,
        LocalDate transactionDate,
        PaymentMethod paymentMethod,
        String referenceNumber,
        String reason,
        String recordedByName,
        Instant createdAt
) {}

