package com.upcraft.stay.billing.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Normalized chronological entry across rent invoices, payments, reversals, and deposits.
 */
public record LedgerEntryResponseDTO(
        UUID id,
        LocalDate entryDate,
        String category,
        String title,
        String reference,
        BigDecimal debitAmount,
        BigDecimal creditAmount,
        String status,
        String notes
) {}

