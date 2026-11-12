package com.upcraft.stay.room.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.upcraft.stay.common.enums.RoomStatus;

/**
 * Detailed property-scoped room response including bed summary breakdown.
 */
public record PropertyRoomResponseDTO(
        UUID id,
        UUID propertyId,
        UUID floorId,
        String floorName,
        Integer floorNumber,
        String number,
        String roomType,
        String sharing,
        Integer capacity,
        Integer occupied,
        BigDecimal baseMonthlyRent,
        BigDecimal defaultDeposit,
        RoomStatus roomStatus,
        List<String> amenities,
        String notes,
        long totalBeds,
        long vacantBeds,
        long occupiedBeds,
        long reservedBeds,
        long maintenanceBeds,
        List<BedResponseDTO> beds,
        LocalDateTime createdAt
) {}

