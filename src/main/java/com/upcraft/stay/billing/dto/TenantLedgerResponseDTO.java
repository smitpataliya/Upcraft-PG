package com.upcraft.stay.billing.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.upcraft.stay.common.dto.PageResponseDTO;

/**
 * Complete resident ledger view showing billed/paid rent totals, deposit held, and paginated entries.
 */
public record TenantLedgerResponseDTO(
        UUID tenantId,
        String tenantName,
        String tenantPhone,
        UUID propertyId,
        String propertyName,
        BigDecimal totalBilled,
        BigDecimal totalPaid,
        BigDecimal totalOutstanding,
        BigDecimal depositHeld,
        PageResponseDTO<LedgerEntryResponseDTO> entries
) {}

