package com.upcraft.stay.room.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for creating a room (and optional auto-generated beds) inside a PG property.
 */
public record CreatePropertyRoomRequestDTO(
        UUID floorId,
        Integer floorNumber,
        @NotBlank(message = "Room number or name is required")
        @Size(max = 100, message = "Room number cannot exceed 100 characters")
        String number,
        @NotBlank(message = "Room type is required")
        @Size(max = 40)
        String roomType,
        @NotBlank(message = "Sharing label is required")
        @Size(max = 80)
        String sharing,
        @NotNull(message = "Capacity is required")
        @Min(value = 1, message = "Capacity must be at least 1")
        @Max(value = 50, message = "Capacity cannot exceed 50 beds")
        Integer capacity,
        @NotNull(message = "Monthly rent is required")
        @DecimalMin(value = "0.00", message = "Monthly rent cannot be negative")
        BigDecimal baseMonthlyRent,
        @DecimalMin(value = "0.00", message = "Default deposit cannot be negative")
        BigDecimal defaultDeposit,
        List<String> amenities,
        String notes,
        Boolean autoCreateBeds
) {}

