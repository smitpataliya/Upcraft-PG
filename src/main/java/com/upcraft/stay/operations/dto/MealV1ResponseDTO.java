package com.upcraft.stay.operations.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import com.upcraft.stay.domain.enums.MealType;

/**
 * Property-scoped food menu item response DTO.
 */
public record MealV1ResponseDTO(
        UUID id,
        UUID propertyId,
        String propertyName,
        String name,
        String description,
        String imageUrl,
        MealType type,
        String servingTime,
        boolean veg,
        boolean available,
        boolean published,
        LocalDate dateScheduled,
        String dayOfWeek,
        LocalDateTime createdAt
) {
}

