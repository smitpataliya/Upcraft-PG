package com.upcraft.stay.tenant.controller;

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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.upcraft.stay.common.constants.AppConstants;
import com.upcraft.stay.common.dto.ApiResponseDTO;
import com.upcraft.stay.common.dto.PageResponseDTO;
import com.upcraft.stay.common.enums.TenantStatus;
import com.upcraft.stay.tenant.dto.CreateTenantRequestDTO;
import com.upcraft.stay.tenant.dto.TenantDetailResponseDTO;
import com.upcraft.stay.tenant.dto.TenantSelfPortalResponseDTO;
import com.upcraft.stay.tenant.dto.TenantSummaryResponseDTO;
import com.upcraft.stay.tenant.dto.UpdateTenantRequestDTO;
import com.upcraft.stay.tenant.service.TenantService;

/**
 * Tenant directory and self-service residency controller.
 */
@RestController
@RequestMapping("/api/v1/tenants")
@RequiredArgsConstructor
public class TenantV1Controller {

    private final TenantService tenantService;

    @GetMapping
    @PreAuthorize("hasAnyRole('PG_OWNER', 'MANAGER')")
    public ResponseEntity<ApiResponseDTO<PageResponseDTO<TenantSummaryResponseDTO>>> listTenants(
            Principal principal,
            @RequestParam(required = false) UUID propertyId,
            @RequestParam(required = false) TenantStatus tenantStatus,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection
    ) {
        PageResponseDTO<TenantSummaryResponseDTO> tenantPage = tenantService.listTenants(
                principal.getName(), propertyId, tenantStatus, search, page, size, sortBy, sortDirection
        );
        return ResponseEntity.ok(ApiResponseDTO.success("Tenants loaded", tenantPage, traceId()));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('PG_MEMBER')")
    public ResponseEntity<ApiResponseDTO<TenantSelfPortalResponseDTO>> getMyResidency(Principal principal) {
        TenantSelfPortalResponseDTO selfPortal = tenantService.getSelfPortal(principal.getName());
        return ResponseEntity.ok(ApiResponseDTO.success("Resident portal loaded", selfPortal, traceId()));
    }

    @GetMapping("/{tenantId}")
    @PreAuthorize("hasAnyRole('PG_OWNER', 'MANAGER')")
    public ResponseEntity<ApiResponseDTO<TenantDetailResponseDTO>> getTenantDetail(
            Principal principal,
            @PathVariable UUID tenantId
    ) {
        TenantDetailResponseDTO detailDTO = tenantService.getTenantDetail(principal.getName(), tenantId);
        return ResponseEntity.ok(ApiResponseDTO.success("Tenant loaded", detailDTO, traceId()));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('PG_OWNER', 'MANAGER')")
    public ResponseEntity<ApiResponseDTO<TenantDetailResponseDTO>> createTenant(
            Principal principal,
            @Valid @RequestBody CreateTenantRequestDTO createTenantRequestDTO
    ) {
        TenantDetailResponseDTO detailDTO = tenantService.createTenant(principal.getName(), createTenantRequestDTO);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseDTO.success("Tenant profile created", detailDTO, traceId()));
    }

    @PutMapping("/{tenantId}")
    @PreAuthorize("hasAnyRole('PG_OWNER', 'MANAGER')")
    public ResponseEntity<ApiResponseDTO<TenantDetailResponseDTO>> updateTenant(
            Principal principal,
            @PathVariable UUID tenantId,
            @Valid @RequestBody UpdateTenantRequestDTO updateTenantRequestDTO
    ) {
        TenantDetailResponseDTO detailDTO = tenantService.updateTenant(
                principal.getName(), tenantId, updateTenantRequestDTO
        );
        return ResponseEntity.ok(ApiResponseDTO.success("Tenant profile updated", detailDTO, traceId()));
    }

    private String traceId() {
        return MDC.get(AppConstants.TRACE_ID_MDC_KEY);
    }
}

