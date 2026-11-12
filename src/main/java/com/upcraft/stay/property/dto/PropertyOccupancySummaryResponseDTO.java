package com.upcraft.stay.property.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.upcraft.stay.common.enums.LateFeeType;
import com.upcraft.stay.common.enums.PropertyStatus;
import com.upcraft.stay.common.enums.PropertyType;

/**
 * Live database-derived property occupancy and configuration summary response DTO.
 */
public record PropertyOccupancySummaryResponseDTO(
        UUID propertyId,
        String name,
        PropertyType propertyType,
        PropertyStatus propertyStatus,
        String city,
        String state,
        String checkInRules,
        String checkOutRules,
        Integer rentDueDay,
        Integer gracePeriodDays,
        LateFeeType lateFeeType,
        BigDecimal lateFeeAmount,
        BigDecimal defaultElectricityRate,
        long totalFloors,
        long totalRooms,
        long totalBeds,
        long vacantBeds,
        long occupiedBeds,
        long reservedBeds,
        long maintenanceBeds,
        long inactiveBeds,
        double occupancyRatePercent
) {}

