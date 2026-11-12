package com.upcraft.stay.billing.controller;

import java.security.Principal;
import java.util.UUID;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.upcraft.stay.billing.dto.CreateCustomInvoiceRequestDTO;
import com.upcraft.stay.billing.dto.GenerateInvoicesRequestDTO;
import com.upcraft.stay.billing.dto.GenerateInvoicesResultResponseDTO;
import com.upcraft.stay.billing.dto.InvoiceDetailResponseDTO;
import com.upcraft.stay.billing.dto.InvoiceSummaryResponseDTO;
import com.upcraft.stay.billing.dto.UpdateInvoiceStatusRequestDTO;
import com.upcraft.stay.billing.service.InvoiceService;
import com.upcraft.stay.common.constants.AppConstants;
import com.upcraft.stay.common.dto.ApiResponseDTO;
import com.upcraft.stay.common.dto.PageResponseDTO;
import com.upcraft.stay.common.enums.InvoiceStatus;

/**
 * Versioned REST controller for rent generation, invoice listing, late fees, and status updates.
 */
@RestController
@RequestMapping("/api/v1/invoices")
@RequiredArgsConstructor
public class InvoiceV1Controller {

    private final InvoiceService invoiceService;

    @GetMapping
    @PreAuthorize("hasAnyRole('PG_OWNER', 'MANAGER')")
    public ResponseEntity<ApiResponseDTO<PageResponseDTO<InvoiceSummaryResponseDTO>>> listInvoices(
            Principal principal,
            @RequestParam(required = false) UUID propertyId,
            @RequestParam(required = false) InvoiceStatus invoiceStatus,
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(required = false) Integer billingYear,
            @RequestParam(required = false) Integer billingMonth,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size,
            @RequestParam(defaultValue = "dueDate") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection
    ) {
        PageResponseDTO<InvoiceSummaryResponseDTO> result = invoiceService.listInvoices(
                principal.getName(), propertyId, invoiceStatus, tenantId,
                billingYear, billingMonth, search, page, size, sortBy, sortDirection
        );
        return ResponseEntity.ok(ApiResponseDTO.success("Invoices loaded", result, traceId()));
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('PG_MEMBER')")
    public ResponseEntity<ApiResponseDTO<PageResponseDTO<InvoiceSummaryResponseDTO>>> listMyInvoices(
            Principal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size,
            @RequestParam(defaultValue = "dueDate") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection
    ) {
        PageResponseDTO<InvoiceSummaryResponseDTO> result = invoiceService.listMyInvoices(
                principal.getName(), page, size, sortBy, sortDirection
        );
        return ResponseEntity.ok(ApiResponseDTO.success("Resident invoices loaded", result, traceId()));
    }

    @GetMapping("/{invoiceId}")
    @PreAuthorize("hasAnyRole('PG_OWNER', 'MANAGER', 'PG_MEMBER')")
    public ResponseEntity<ApiResponseDTO<InvoiceDetailResponseDTO>> getInvoiceDetail(
            Principal principal,
            @PathVariable UUID invoiceId
    ) {
        InvoiceDetailResponseDTO detail = invoiceService.getInvoiceDetail(principal.getName(), invoiceId);
        return ResponseEntity.ok(ApiResponseDTO.success("Invoice detail loaded", detail, traceId()));
    }

    @PostMapping("/generate-rent")
    @PreAuthorize("hasAnyRole('PG_OWNER', 'MANAGER')")
    public ResponseEntity<ApiResponseDTO<GenerateInvoicesResultResponseDTO>> generateMonthlyRent(
            Principal principal,
            @Valid @RequestBody GenerateInvoicesRequestDTO generateRequestDTO
    ) {
        GenerateInvoicesResultResponseDTO result = invoiceService.generateMonthlyRentInvoices(
                principal.getName(), generateRequestDTO
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseDTO.success("Monthly rent invoices processed", result, traceId()));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('PG_OWNER', 'MANAGER')")
    public ResponseEntity<ApiResponseDTO<InvoiceDetailResponseDTO>> createCustomInvoice(
            Principal principal,
            @Valid @RequestBody CreateCustomInvoiceRequestDTO customRequestDTO
    ) {
        InvoiceDetailResponseDTO detail = invoiceService.createCustomInvoice(principal.getName(), customRequestDTO);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseDTO.success("Custom invoice created", detail, traceId()));
    }

    @PatchMapping("/{invoiceId}/status")
    @PreAuthorize("hasAnyRole('PG_OWNER', 'MANAGER')")
    public ResponseEntity<ApiResponseDTO<InvoiceDetailResponseDTO>> updateInvoiceStatus(
            Principal principal,
            @PathVariable UUID invoiceId,
            @Valid @RequestBody UpdateInvoiceStatusRequestDTO updateStatusRequestDTO
    ) {
        InvoiceDetailResponseDTO detail = invoiceService.updateInvoiceStatus(
                principal.getName(), invoiceId, updateStatusRequestDTO
        );
        return ResponseEntity.ok(ApiResponseDTO.success("Invoice status updated", detail, traceId()));
    }

    @PostMapping("/{invoiceId}/late-fee")
    @PreAuthorize("hasAnyRole('PG_OWNER', 'MANAGER')")
    public ResponseEntity<ApiResponseDTO<InvoiceDetailResponseDTO>> applyLateFee(
            Principal principal,
            @PathVariable UUID invoiceId
    ) {
        InvoiceDetailResponseDTO detail = invoiceService.applyLateFee(principal.getName(), invoiceId);
        return ResponseEntity.ok(ApiResponseDTO.success("Late fee applied to invoice", detail, traceId()));
    }

    private String traceId() {
        return MDC.get(AppConstants.TRACE_ID_MDC_KEY);
    }
}

