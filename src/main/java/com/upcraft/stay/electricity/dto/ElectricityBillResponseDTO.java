package com.upcraft.stay.electricity.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.upcraft.stay.common.enums.ElectricityBillStatus;
import com.upcraft.stay.common.enums.SplitMethod;

/**
 * Resident-facing or manager-facing electricity bill share with frozen reading and tariff snapshots.
 */
public record ElectricityBillResponseDTO(
        UUID id,
        UUID propertyId,
        String propertyName,
        UUID meterId,
        String meterNumber,
        UUID readingId,
        UUID tenancyId,
        UUID tenantId,
        String tenantName,
        String tenantPhone,
        String roomNumber,
        String bedCode,
        UUID invoiceId,
        String invoiceNumber,
        String billNumber,
        Integer billingYear,
        Integer billingMonth,
        LocalDate readingDate,
        BigDecimal previousReading,
        BigDecimal currentReading,
        BigDecimal totalMeterUnits,
        SplitMethod splitMethodSnapshot,
        BigDecimal sharePercentage,
        BigDecimal unitsAllocated,
        BigDecimal ratePerUnitSnapshot,
        BigDecimal energyCharge,
        BigDecimal fixedChargeShare,
        BigDecimal additionalChargeShare,
        BigDecimal taxAmountShare,
        BigDecimal creditShare,
        BigDecimal totalAmount,
        ElectricityBillStatus billStatus,
        UUID photoFileId,
        String photoDownloadPath,
        Instant createdAt
) {}

