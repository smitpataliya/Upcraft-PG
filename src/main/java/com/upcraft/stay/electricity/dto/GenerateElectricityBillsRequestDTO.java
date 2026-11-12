package com.upcraft.stay.electricity.dto;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;

import com.upcraft.stay.common.enums.SplitMethod;

/**
 * Request payload to generate itemized electricity bills and optional linked invoices from an approved reading.
 */
public record GenerateElectricityBillsRequestDTO(
        SplitMethod splitMethodOverride,
        Boolean linkToInvoice,
        LocalDate dueDate,
        List<@Valid BillAllocationOverrideRequestDTO> allocations
) {}

