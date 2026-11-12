package com.upcraft.stay.operations.dto;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.upcraft.stay.domain.enums.MealType;

/**
 * Request payload for adding or updating a property-scoped meal entry.
 */
public record UpsertMealV1RequestDTO(
        @NotNull(message = "Property ID is required")
        UUID propertyId,
        @NotBlank(message = "Meal name is required")
        @Size(max = 200, message = "Meal name cannot exceed 200 characters")
        String name,
        @Size(max = 500, message = "Description cannot exceed 500 characters")
        String description,
        @Size(max = 500, message = "Image URL cannot exceed 500 characters")
        String imageUrl,
        @NotNull(message = "Meal type is required")
        MealType type,
        @Size(max = 100, message = "Serving time cannot exceed 100 characters")
        String servingTime,
        boolean veg,
        boolean available,
        boolean published,
        @NotNull(message = "Scheduled date is required")
        LocalDate dateScheduled
) {
}

