package com.upcraft.stay.electricity.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Property electricity consumption and billing analytics across a selected year/month filter.
 */
public record ConsumptionReportResponseDTO(
        long totalMeters,
        long activeMeters,
        long readingsSubmitted,
        long pendingApprovals,
        long unusualReadingsCount,
        BigDecimal totalUnitsConsumed,
        BigDecimal totalElectricityAmount,
        BigDecimal averageUnitsPerReading,
        List<MeterConsumptionBreakdownResponseDTO> meterBreakdowns
) {}

