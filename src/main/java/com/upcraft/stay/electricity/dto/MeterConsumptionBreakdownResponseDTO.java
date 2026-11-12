package com.upcraft.stay.electricity.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.upcraft.stay.common.enums.MeterScope;

/**
 * Per-meter consumption and charge summary inside the property consumption report.
 */
public record MeterConsumptionBreakdownResponseDTO(
        UUID meterId,
        String meterNumber,
        String meterLabel,
        MeterScope meterScope,
        String locationLabel,
        long readingCount,
        BigDecimal totalUnits,
        BigDecimal totalAmount,
        long unusualCount
) {}

