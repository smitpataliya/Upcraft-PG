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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.upcraft.stay.common.constants.AppConstants;
import com.upcraft.stay.common.dto.ApiResponseDTO;
import com.upcraft.stay.common.dto.PageResponseDTO;
import com.upcraft.stay.common.enums.TenancyStatus;
import com.upcraft.stay.room.dto.BedResponseDTO;
import com.upcraft.stay.tenant.dto.MoveInRequestDTO;
import com.upcraft.stay.tenant.dto.MoveOutRequestDTO;
import com.upcraft.stay.tenant.dto.ReserveBedRequestDTO;
import com.upcraft.stay.tenant.dto.TenancyResponseDTO;
import com.upcraft.stay.tenant.dto.TransferBedRequestDTO;
import com.upcraft.stay.tenant.service.TenancyService;

/**
 * Transactional occupancy workflow controller for move-in, bed reserve, bed transfer, and move-out settlement.
 */
@RestController
@RequestMapping("/api/v1/tenancies")
@RequiredArgsConstructor
public class TenancyV1Controller {

    private final TenancyService tenancyService;

    @GetMapping
    @PreAuthorize("hasAnyRole('PG_OWNER', 'MANAGER')")
    public ResponseEntity<ApiResponseDTO<PageResponseDTO<TenancyResponseDTO>>> listTenancies(
            Principal principal,
            @RequestParam(required = false) UUID propertyId,
            @RequestParam(required = false) TenancyStatus tenancyStatus,
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size,
            @RequestParam(defaultValue = "moveInDate") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection
    ) {
        PageResponseDTO<TenancyResponseDTO> tenancyPage = tenancyService.listTenancies(
                principal.getName(), propertyId, tenancyStatus, tenantId, page, size, sortBy, sortDirection
        );
        return ResponseEntity.ok(ApiResponseDTO.success("Tenancies loaded", tenancyPage, traceId()));
    }

    @PostMapping("/reserve")
    @PreAuthorize("hasAnyRole('PG_OWNER', 'MANAGER')")
    public ResponseEntity<ApiResponseDTO<BedResponseDTO>> reserveBed(
            Principal principal,
            @Valid @RequestBody ReserveBedRequestDTO reserveBedRequestDTO
    ) {
        BedResponseDTO bedDTO = tenancyService.reserveBed(principal.getName(), reserveBedRequestDTO);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseDTO.success("Bed reserved", bedDTO, traceId()));
    }

    @PostMapping("/move-in")
    @PreAuthorize("hasAnyRole('PG_OWNER', 'MANAGER')")
    public ResponseEntity<ApiResponseDTO<TenancyResponseDTO>> moveIn(
            Principal principal,
            @Valid @RequestBody MoveInRequestDTO moveInRequestDTO
    ) {
        TenancyResponseDTO tenancyDTO = tenancyService.moveIn(principal.getName(), moveInRequestDTO);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseDTO.success("Tenant moved in successfully", tenancyDTO, traceId()));
    }

    @PostMapping("/{tenancyId}/transfer")
    @PreAuthorize("hasAnyRole('PG_OWNER', 'MANAGER')")
    public ResponseEntity<ApiResponseDTO<TenancyResponseDTO>> transferBed(
            Principal principal,
            @PathVariable UUID tenancyId,
            @Valid @RequestBody TransferBedRequestDTO transferBedRequestDTO
    ) {
        TenancyResponseDTO tenancyDTO = tenancyService.transferBed(
                principal.getName(), tenancyId, transferBedRequestDTO
        );
        return ResponseEntity.ok(ApiResponseDTO.success("Bed transfer completed", tenancyDTO, traceId()));
    }

    @PostMapping("/{tenancyId}/move-out")
    @PreAuthorize("hasAnyRole('PG_OWNER', 'MANAGER')")
    public ResponseEntity<ApiResponseDTO<TenancyResponseDTO>> moveOut(
            Principal principal,
            @PathVariable UUID tenancyId,
            @Valid @RequestBody MoveOutRequestDTO moveOutRequestDTO
    ) {
        TenancyResponseDTO tenancyDTO = tenancyService.moveOut(principal.getName(), tenancyId, moveOutRequestDTO);
        return ResponseEntity.ok(ApiResponseDTO.success("Move-out settlement recorded", tenancyDTO, traceId()));
    }

    private String traceId() {
        return MDC.get(AppConstants.TRACE_ID_MDC_KEY);
    }
}

