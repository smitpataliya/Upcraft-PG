package com.upcraft.stay.tenant.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Move-in wizard request creating or selecting a tenant, allocating a vacant bed,
 * capturing initial deposit and meter reading, and optionally sending portal access.
 */
public record MoveInRequestDTO(
        @NotNull(message = "Bed ID is required")
        UUID bedId,
        UUID existingTenantId,
        @Size(max = 150)
        String fullName,
        @Size(max = 30)
        String phone,
        @Email(message = "Invalid email format")
        @Size(max = 255)
        String email,
        @Size(max = 150)
        String emergencyContact,
        @Size(max = 30)
        String emergencyPhone,
        @Size(max = 500)
        String address,
        @NotNull(message = "Move-in date is required")
        LocalDate moveInDate,
        LocalDate expectedMoveOutDate,
        @DecimalMin(value = "0.00", message = "Monthly rent cannot be negative")
        BigDecimal agreedMonthlyRent,
        @DecimalMin(value = "0.00", message = "Security deposit cannot be negative")
        BigDecimal securityDepositAmount,
        @DecimalMin(value = "0.00", message = "Received deposit cannot be negative")
        BigDecimal depositReceivedAmount,
        @Min(1)
        @Max(28)
        Integer rentDueDay,
        @DecimalMin("0.00")
        BigDecimal initialMeterReading,
        LocalDate agreementStartDate,
        LocalDate agreementEndDate,
        String moveInNotes,
        Boolean sendPortalInvite
) {}

