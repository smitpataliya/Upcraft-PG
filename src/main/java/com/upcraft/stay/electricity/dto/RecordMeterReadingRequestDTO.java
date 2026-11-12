package com.upcraft.stay.electricity.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Request payload to record a monthly electricity reading with optional photo attachment and reset workflow.
 */
public record RecordMeterReadingRequestDTO(
        @NotNull(message = "Billing year is required")
        @Min(value = 2020, message = "Invalid billing year")
        @Max(value = 2100, message = "Invalid billing year")
        Integer billingYear,

        @NotNull(message = "Billing month is required")
        @Min(value = 1, message = "Billing month must be 1-12")
        @Max(value = 12, message = "Billing month must be 1-12")
        Integer billingMonth,

        @NotNull(message = "Reading date is required")
        LocalDate readingDate,

        @DecimalMin(value = "0.0", message = "Previous value cannot be negative")
        BigDecimal previousValue,

        @NotNull(message = "Current reading is required")
        @DecimalMin(value = "0.0", message = "Current value cannot be negative")
        BigDecimal currentValue,

        @DecimalMin(value = "0.0", message = "Additional charge cannot be negative")
        BigDecimal additionalCharge,

        @DecimalMin(value = "0.0", message = "Tax amount cannot be negative")
        BigDecimal taxAmount,

        @DecimalMin(value = "0.0", message = "Credit amount cannot be negative")
        BigDecimal creditAmount,

        UUID photoFileId,
        Boolean allowResetOrCorrection,
        UUID correctionOfReadingId,
        String correctionReason,
        Boolean autoApprove,
        String notes
) {}

