package com.upcraft.stay.room.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.upcraft.stay.common.enums.BedStatus;

/**
 * Request DTO for creating an individual bed inside a room.
 */
public record CreateBedRequestDTO(
        @NotBlank(message = "Bed code is required")
        @Size(max = 60, message = "Bed code cannot exceed 60 characters")
        String bedCode,
        @DecimalMin(value = "0.00", message = "Monthly rent cannot be negative")
        BigDecimal monthlyRent,
        @DecimalMin(value = "0.00", message = "Default deposit cannot be negative")
        BigDecimal defaultDeposit,
        BedStatus bedStatus,
        LocalDate availableFrom,
        String notes
) {}

