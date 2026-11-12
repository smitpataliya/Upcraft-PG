package com.upcraft.stay.room.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for updating bed pricing, availability date, and metadata.
 */
public record UpdateBedRequestDTO(
        @NotBlank(message = "Bed code is required")
        @Size(max = 60, message = "Bed code cannot exceed 60 characters")
        String bedCode,
        @DecimalMin(value = "0.00", message = "Monthly rent cannot be negative")
        BigDecimal monthlyRent,
        @DecimalMin(value = "0.00", message = "Default deposit cannot be negative")
        BigDecimal defaultDeposit,
        LocalDate availableFrom,
        String notes
) {}

