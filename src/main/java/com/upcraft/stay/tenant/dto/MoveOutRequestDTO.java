package com.upcraft.stay.tenant.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import com.upcraft.stay.common.enums.BedStatus;
import com.upcraft.stay.common.enums.RefundStatus;

/**
 * Move-out settlement request capturing final meter reading, unpaid dues, deposit deductions,
 * refund decision, and target post-checkout bed status (defaults to VACANT).
 */
public record MoveOutRequestDTO(
        @NotNull(message = "Actual move-out date is required")
        LocalDate actualMoveOutDate,
        @DecimalMin("0.00")
        BigDecimal finalMeterReading,
        @DecimalMin("0.00")
        BigDecimal unpaidDuesSettled,
        @DecimalMin("0.00")
        BigDecimal deductionsAmount,
        String deductionReason,
        RefundStatus refundStatus,
        BedStatus releaseBedStatus,
        String moveOutNotes
) {}

