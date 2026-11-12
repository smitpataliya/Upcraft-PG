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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.upcraft.stay.billing.dto.DepositAccountSummaryResponseDTO;
import com.upcraft.stay.billing.dto.DepositTransactionResponseDTO;
import com.upcraft.stay.billing.dto.RecordDepositTransactionRequestDTO;
import com.upcraft.stay.billing.service.DepositService;
import com.upcraft.stay.common.constants.AppConstants;
import com.upcraft.stay.common.dto.ApiResponseDTO;

/**
 * Versioned REST controller for segregated tenancy security deposit ledgers and settlements.
 */
@RestController
@RequestMapping("/api/v1/tenancies/{tenancyId}/deposits")
@RequiredArgsConstructor
public class DepositV1Controller {

    private final DepositService depositService;

    @GetMapping
    @PreAuthorize("hasAnyRole('PG_OWNER', 'MANAGER', 'PG_MEMBER')")
    public ResponseEntity<ApiResponseDTO<DepositAccountSummaryResponseDTO>> getDepositSummary(
            Principal principal,
            @PathVariable UUID tenancyId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size
    ) {
        DepositAccountSummaryResponseDTO summary = depositService.getTenancyDepositSummary(
                principal.getName(), tenancyId, page, size
        );
        return ResponseEntity.ok(ApiResponseDTO.success("Deposit ledger loaded", summary, traceId()));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('PG_OWNER', 'MANAGER')")
    public ResponseEntity<ApiResponseDTO<DepositTransactionResponseDTO>> recordDepositTransaction(
            Principal principal,
            @PathVariable UUID tenancyId,
            @Valid @RequestBody RecordDepositTransactionRequestDTO requestDTO
    ) {
        DepositTransactionResponseDTO tx = depositService.recordDepositTransaction(
                principal.getName(), tenancyId, requestDTO
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseDTO.success("Deposit transaction recorded", tx, traceId()));
    }

    private String traceId() {
        return MDC.get(AppConstants.TRACE_ID_MDC_KEY);
    }
}

