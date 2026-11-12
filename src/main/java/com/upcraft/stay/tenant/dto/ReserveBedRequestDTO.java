package com.upcraft.stay.tenant.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/**
 * Request DTO to reserve a vacant bed for an upcoming check-in date.
 */
public record ReserveBedRequestDTO(
        @NotNull(message = "Bed ID is required")
        UUID bedId,
        LocalDate availableFrom,
        Instant reservedUntil,
        String notes
) {}

