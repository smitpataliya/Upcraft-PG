package com.upcraft.stay.tenant.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.upcraft.stay.common.enums.RefundStatus;
import com.upcraft.stay.common.enums.TenancyStatus;

/**
 * Tenancy response DTO exposing complete move-in, bed allocation, and move-out settlement details.
 */
public record TenancyResponseDTO(
        UUID id,
        UUID organizationId,
        UUID propertyId,
        String propertyName,
        UUID tenantId,
        String tenantName,
        String tenantPhone,
        UUID roomId,
        String roomNumber,
        UUID bedId,
        String bedCode,
        TenancyStatus tenancyStatus,
        LocalDate moveInDate,
        LocalDate expectedMoveOutDate,
        LocalDate actualMoveOutDate,
        BigDecimal agreedMonthlyRent,
        BigDecimal securityDepositAmount,
        BigDecimal depositReceivedAmount,
        Integer rentDueDay,
        BigDecimal initialMeterReading,
        BigDecimal finalMeterReading,
        BigDecimal unpaidDuesSettled,
        BigDecimal deductionsAmount,
        String deductionReason,
        BigDecimal refundableDepositAmount,
        RefundStatus refundStatus,
        LocalDate agreementStartDate,
        LocalDate agreementEndDate,
        String moveInNotes,
        String moveOutNotes,
        Instant createdAt
) {}

