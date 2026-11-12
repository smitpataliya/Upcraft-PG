package com.upcraft.stay.billing.controller;

import java.security.Principal;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.upcraft.stay.billing.dto.FinancialSummaryResponseDTO;
import com.upcraft.stay.billing.dto.TenantLedgerResponseDTO;
import com.upcraft.stay.billing.service.PaymentService;
import com.upcraft.stay.common.constants.AppConstants;
import com.upcraft.stay.common.dto.ApiResponseDTO;

/**
 * Financial overview metrics and per-tenant unified account ledger endpoints.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class BillingV1Controller {

    private final PaymentService paymentService;

    @GetMapping("/billing/summary")
    @PreAuthorize("hasAnyRole('PG_OWNER', 'MANAGER')")
    public ResponseEntity<ApiResponseDTO<FinancialSummaryResponseDTO>> getFinancialSummary(
            Principal principal,
            @RequestParam(required = false) UUID propertyId,
            @RequestParam(required = false) Integer billingYear,
            @RequestParam(required = false) Integer billingMonth
    ) {
        FinancialSummaryResponseDTO summary = paymentService.getFinancialSummary(
                principal.getName(), propertyId, billingYear, billingMonth
        );
        return ResponseEntity.ok(ApiResponseDTO.success("Financial summary loaded", summary, traceId()));
    }

    @GetMapping("/tenants/{tenantId}/ledger")
    @PreAuthorize("hasAnyRole('PG_OWNER', 'MANAGER', 'PG_MEMBER')")
    public ResponseEntity<ApiResponseDTO<TenantLedgerResponseDTO>> getTenantLedger(
            Principal principal,
            @PathVariable UUID tenantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size
    ) {
        TenantLedgerResponseDTO ledger = paymentService.getTenantLedger(
                principal.getName(), tenantId, page, size
        );
        return ResponseEntity.ok(ApiResponseDTO.success("Tenant ledger loaded", ledger, traceId()));
    }

    private String traceId() {
        return MDC.get(AppConstants.TRACE_ID_MDC_KEY);
    }
}

