package com.upcraft.stay.room.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO describing a property floor and room count.
 */
public record FloorResponseDTO(
        UUID id,
        UUID propertyId,
        Integer floorNumber,
        String name,
        String notes,
        long roomCount,
        Instant createdAt
) {}

