package com.upcraft.stay.room.dto;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;

import com.upcraft.stay.common.enums.BedStatus;

/**
 * Request DTO for transitioning operational bed status (e.g., vacant, maintenance, inactive, reserved).
 */
public record UpdateBedStatusRequestDTO(
        @NotNull(message = "Target bed status is required")
        BedStatus bedStatus,
        LocalDate availableFrom,
        Instant reservedUntil,
        String notes
) {}

