package com.upcraft.stay.room.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.upcraft.stay.common.enums.BedStatus;

/**
 * Filtered bed vacancy response DTO containing property, floor, room, rent, and availability metadata.
 */
public record BedAvailabilityResponseDTO(
        UUID bedId,
        UUID propertyId,
        String propertyName,
        String city,
        UUID floorId,
        String floorName,
        Integer floorNumber,
        UUID roomId,
        String roomNumber,
        String roomType,
        String sharing,
        List<String> amenities,
        String bedCode,
        BigDecimal monthlyRent,
        BigDecimal defaultDeposit,
        BedStatus bedStatus,
        LocalDate availableFrom,
        Instant reservedUntil
) {}

