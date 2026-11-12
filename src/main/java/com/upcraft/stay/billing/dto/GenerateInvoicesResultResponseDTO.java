package com.upcraft.stay.billing.dto;

import java.util.List;

/**
 * Result summary of idempotent monthly rent invoice batch generation.
 */
public record GenerateInvoicesResultResponseDTO(
        Integer billingYear,
        Integer billingMonth,
        int generatedCount,
        int skippedAlreadyBilledCount,
        List<InvoiceSummaryResponseDTO> generatedInvoices
) {}

