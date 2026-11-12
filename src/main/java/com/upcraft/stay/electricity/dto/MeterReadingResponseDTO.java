package com.upcraft.stay.electricity.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.upcraft.stay.common.enums.MeterReadingStatus;
import com.upcraft.stay.common.enums.MeterScope;
import com.upcraft.stay.common.enums.SplitMethod;

/**
 * Meter reading response preserving tariff snapshots, photo proof link, unusual usage flag, and approval status.
 */
public record MeterReadingResponseDTO(
        UUID id,
        UUID propertyId,
        String propertyName,
        UUID meterId,
        String meterNumber,
        MeterScope meterScope,
        SplitMethod splitMethod,
        String roomNumber,
        String bedCode,
        Integer billingYear,
        Integer billingMonth,
        LocalDate readingDate,
        BigDecimal previousValue,
        BigDecimal currentValue,
        BigDecimal unitsConsumed,
        BigDecimal ratePerUnitSnapshot,
        BigDecimal fixedChargeSnapshot,
        BigDecimal energyCharge,
        BigDecimal additionalCharge,
        BigDecimal taxAmount,
        BigDecimal creditAmount,
        BigDecimal totalAmount,
        MeterReadingStatus readingStatus,
        UUID photoFileId,
        String photoDownloadPath,
        boolean unusualUsage,
        String unusualUsageReason,
        boolean meterResetApplied,
        String correctionReason,
        String rejectionReason,
        String recordedByName,
        String approvedByName,
        Instant approvedAt,
        String notes,
        Instant createdAt
) {}

