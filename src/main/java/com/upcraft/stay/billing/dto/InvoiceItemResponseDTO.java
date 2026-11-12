package com.upcraft.stay.billing.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.upcraft.stay.common.enums.InvoiceItemType;

/**
 * Response projection for a single invoice line item.
 */
public record InvoiceItemResponseDTO(
        UUID id,
        InvoiceItemType itemType,
        String description,
        BigDecimal quantity,
        BigDecimal unitAmount,
        BigDecimal lineTotal,
        Integer sortOrder
) {}

