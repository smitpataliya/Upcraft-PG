package com.upcraft.stay.billing.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

import com.upcraft.stay.billing.dto.CreateCustomInvoiceRequestDTO;
import com.upcraft.stay.billing.dto.GenerateInvoicesRequestDTO;
import com.upcraft.stay.billing.dto.GenerateInvoicesResultResponseDTO;
import com.upcraft.stay.billing.dto.InvoiceDetailResponseDTO;
import com.upcraft.stay.billing.dto.InvoiceSummaryResponseDTO;
import com.upcraft.stay.billing.dto.UpdateInvoiceStatusRequestDTO;
import com.upcraft.stay.billing.entity.InvoiceEntity;
import com.upcraft.stay.common.dto.PageResponseDTO;
import com.upcraft.stay.common.enums.InvoiceStatus;
import com.upcraft.stay.tenant.entity.TenancyEntity;

/**
 * Service contract for idempotent monthly rent generation and invoice management.
 */
public interface InvoiceService {

    GenerateInvoicesResultResponseDTO generateMonthlyRentInvoices(
            String principalEmail,
            GenerateInvoicesRequestDTO generateRequestDTO
    );

    InvoiceDetailResponseDTO createCustomInvoice(
            String principalEmail,
            CreateCustomInvoiceRequestDTO customInvoiceRequestDTO
    );

    PageResponseDTO<InvoiceSummaryResponseDTO> listInvoices(
            String principalEmail,
            UUID propertyId,
            InvoiceStatus invoiceStatus,
            UUID tenantId,
            Integer billingYear,
            Integer billingMonth,
            String search,
            int page,
            Integer size,
            String sortBy,
            String sortDirection
    );

    PageResponseDTO<InvoiceSummaryResponseDTO> listMyInvoices(
            String principalEmail,
            int page,
            Integer size,
            String sortBy,
            String sortDirection
    );

    InvoiceDetailResponseDTO getInvoiceDetail(String principalEmail, UUID invoiceId);

    InvoiceDetailResponseDTO updateInvoiceStatus(
            String principalEmail,
            UUID invoiceId,
            UpdateInvoiceStatusRequestDTO updateStatusRequestDTO
    );

    InvoiceDetailResponseDTO applyLateFee(String principalEmail, UUID invoiceId);

    InvoiceEntity createElectricityInvoice(
            TenancyEntity tenancy,
            YearMonth billingPeriod,
            LocalDate dueDate,
            String meterNumber,
            BigDecimal unitsAllocated,
            BigDecimal ratePerUnitSnapshot,
            BigDecimal energyCharge,
            BigDecimal fixedAndAdditionalShare,
            BigDecimal taxAmountShare,
            BigDecimal creditShare
    );
}

