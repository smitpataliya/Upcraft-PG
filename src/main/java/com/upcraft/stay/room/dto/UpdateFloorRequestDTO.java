package com.upcraft.stay.room.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for updating a floor inside a PG property.
 */
public record UpdateFloorRequestDTO(
        @NotNull(message = "Floor number is required")
        Integer floorNumber,
        @NotBlank(message = "Floor name is required")
        @Size(max = 100, message = "Floor name cannot exceed 100 characters")
        String name,
        String notes
) {}

