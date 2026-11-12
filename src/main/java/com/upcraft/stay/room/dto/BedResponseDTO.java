package com.upcraft.stay.room.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.upcraft.stay.common.enums.BedStatus;

/**
 * Bed summary response DTO without leaking unrelated tenant personal details.
 */
public record BedResponseDTO(
        UUID id,
        UUID propertyId,
        UUID roomId,
        String roomNumber,
        String bedCode,
        BigDecimal monthlyRent,
        BigDecimal defaultDeposit,
        BedStatus bedStatus,
        LocalDate availableFrom,
        Instant reservedUntil,
        String notes,
        UUID activeTenancyId,
        String occupantName
) {}

