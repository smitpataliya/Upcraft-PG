package com.upcraft.stay.billing.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.upcraft.stay.common.enums.DepositTransactionType;
import com.upcraft.stay.common.enums.PaymentMethod;

/**
 * Request payload to record security deposit collection, deduction, or refund movement.
 */
public record RecordDepositTransactionRequestDTO(
        @NotNull(message = "Deposit transaction type is required")
        DepositTransactionType transactionType,

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
        BigDecimal amount,

        LocalDate transactionDate,

        PaymentMethod paymentMethod,

        @Size(max = 120, message = "Reference number cannot exceed 120 characters")
        String referenceNumber,

        @NotBlank(message = "Reason or description is required for deposit movements")
        @Size(max = 1000, message = "Reason cannot exceed 1000 characters")
        String reason
) {}

