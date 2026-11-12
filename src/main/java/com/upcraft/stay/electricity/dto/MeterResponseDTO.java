package com.upcraft.stay.electricity.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.upcraft.stay.common.enums.MeterScope;
import com.upcraft.stay.common.enums.MeterStatus;
import com.upcraft.stay.common.enums.SplitMethod;

/**
 * Electricity meter summary with assigned location, tariff rates, and latest reading snapshot.
 */
public record MeterResponseDTO(
        UUID id,
        UUID organizationId,
        UUID propertyId,
        String propertyName,
        UUID roomId,
        String roomNumber,
        UUID bedId,
        String bedCode,
        String meterNumber,
        String meterLabel,
        MeterScope meterScope,
        BigDecimal unitRate,
        BigDecimal fixedCharge,
        SplitMethod splitMethod,
        BigDecimal initialReading,
        BigDecimal lastReadingValue,
        LocalDate lastReadingDate,
        MeterStatus meterStatus,
        String notes,
        Instant createdAt
) {}

