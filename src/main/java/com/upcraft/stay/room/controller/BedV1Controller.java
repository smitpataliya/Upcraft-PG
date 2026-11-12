package com.upcraft.stay.room.controller;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
import com.upcraft.stay.common.enums.BedStatus;
import com.upcraft.stay.room.dto.BedAvailabilityResponseDTO;
import com.upcraft.stay.room.dto.BedResponseDTO;
import com.upcraft.stay.room.dto.CreateBedRequestDTO;
import com.upcraft.stay.room.dto.UpdateBedRequestDTO;
import com.upcraft.stay.room.dto.UpdateBedStatusRequestDTO;
import com.upcraft.stay.room.service.BedService;
import com.upcraft.stay.tenant.dto.TenancyResponseDTO;

/**
 * Bed inventory, vacancy search, status transitions, and allocation history controller.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class BedV1Controller {

    private final BedService bedService;

    @GetMapping("/rooms/{roomId}/beds")
    @PreAuthorize("hasAnyRole('PG_OWNER', 'MANAGER')")
    public ResponseEntity<ApiResponseDTO<PageResponseDTO<BedResponseDTO>>> listRoomBeds(
            Principal principal,
            @PathVariable UUID roomId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size,
            @RequestParam(defaultValue = "bedCode") String sortBy,
            @RequestParam(defaultValue = "ASC") String sortDirection
    ) {
        PageResponseDTO<BedResponseDTO> bedPage = bedService.listRoomBeds(
                principal.getName(), roomId, page, size, sortBy, sortDirection
        );
        return ResponseEntity.ok(ApiResponseDTO.success("Beds loaded", bedPage, traceId()));
    }

    @PostMapping("/rooms/{roomId}/beds")
    @PreAuthorize("hasRole('PG_OWNER')")
    public ResponseEntity<ApiResponseDTO<BedResponseDTO>> createBed(
            Principal principal,
            @PathVariable UUID roomId,
            @Valid @RequestBody CreateBedRequestDTO createBedRequestDTO
    ) {
        BedResponseDTO bedDTO = bedService.createBed(principal.getName(), roomId, createBedRequestDTO);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseDTO.success("Bed created", bedDTO, traceId()));
    }

    @PutMapping("/beds/{bedId}")
    @PreAuthorize("hasRole('PG_OWNER')")
    public ResponseEntity<ApiResponseDTO<BedResponseDTO>> updateBed(
            Principal principal,
            @PathVariable UUID bedId,
            @Valid @RequestBody UpdateBedRequestDTO updateBedRequestDTO
    ) {
        BedResponseDTO bedDTO = bedService.updateBed(principal.getName(), bedId, updateBedRequestDTO);
        return ResponseEntity.ok(ApiResponseDTO.success("Bed updated", bedDTO, traceId()));
    }

    @PatchMapping("/beds/{bedId}/status")
    @PreAuthorize("hasAnyRole('PG_OWNER', 'MANAGER')")
    public ResponseEntity<ApiResponseDTO<BedResponseDTO>> updateBedStatus(
            Principal principal,
            @PathVariable UUID bedId,
            @Valid @RequestBody UpdateBedStatusRequestDTO statusRequestDTO
    ) {
        BedResponseDTO bedDTO = bedService.updateBedStatus(principal.getName(), bedId, statusRequestDTO);
        return ResponseEntity.ok(ApiResponseDTO.success("Bed status updated", bedDTO, traceId()));
    }

    @GetMapping("/beds/availability")
    @PreAuthorize("hasAnyRole('PG_OWNER', 'MANAGER')")
    public ResponseEntity<ApiResponseDTO<PageResponseDTO<BedAvailabilityResponseDTO>>> findAvailableBeds(
            Principal principal,
            @RequestParam(required = false) UUID propertyId,
            @RequestParam(required = false) BedStatus bedStatus,
            @RequestParam(required = false) String roomType,
            @RequestParam(required = false) BigDecimal maxRent,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate availableBefore,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size,
            @RequestParam(defaultValue = "bedCode") String sortBy,
            @RequestParam(defaultValue = "ASC") String sortDirection
    ) {
        PageResponseDTO<BedAvailabilityResponseDTO> availabilityPage = bedService.findAvailableBeds(
                principal.getName(), propertyId, bedStatus, roomType, maxRent, availableBefore,
                page, size, sortBy, sortDirection
        );
        return ResponseEntity.ok(ApiResponseDTO.success("Bed availability loaded", availabilityPage, traceId()));
    }

    @GetMapping("/beds/{bedId}/history")
    @PreAuthorize("hasAnyRole('PG_OWNER', 'MANAGER')")
    public ResponseEntity<ApiResponseDTO<PageResponseDTO<TenancyResponseDTO>>> getBedHistory(
            Principal principal,
            @PathVariable UUID bedId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size,
            @RequestParam(defaultValue = "moveInDate") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection
    ) {
        PageResponseDTO<TenancyResponseDTO> historyPage = bedService.getBedHistory(
                principal.getName(), bedId, page, size, sortBy, sortDirection
        );
        return ResponseEntity.ok(ApiResponseDTO.success("Bed allocation history loaded", historyPage, traceId()));
    }

    private String traceId() {
        return MDC.get(AppConstants.TRACE_ID_MDC_KEY);
    }
}

