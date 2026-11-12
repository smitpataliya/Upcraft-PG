package com.upcraft.stay.property.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.upcraft.stay.common.enums.LateFeeType;
import com.upcraft.stay.common.enums.PropertyStatus;
import com.upcraft.stay.common.enums.PropertyType;

/**
 * Summary response DTO for a PG property.
 */
public record PropertySummaryResponseDTO(
        UUID id,
        UUID organizationId,
        String name,
        PropertyType propertyType,
        String addressLine,
        String landmark,
        String city,
        String state,
        String pinCode,
        String country,
        String contactPhone,
        String contactEmail,
        Integer rentDueDay,
        Integer gracePeriodDays,
        LateFeeType lateFeeType,
        BigDecimal lateFeeAmount,
        BigDecimal defaultElectricityRate,
        PropertyStatus propertyStatus,
        Instant createdAt
) {}

