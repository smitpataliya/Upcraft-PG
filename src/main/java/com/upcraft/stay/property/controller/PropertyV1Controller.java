package com.upcraft.stay.property.controller;

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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.upcraft.stay.common.constants.AppConstants;
import com.upcraft.stay.common.dto.ApiResponseDTO;
import com.upcraft.stay.common.dto.PageResponseDTO;
import com.upcraft.stay.property.dto.CreatePropertyRequestDTO;
import com.upcraft.stay.property.dto.PropertyOccupancySummaryResponseDTO;
import com.upcraft.stay.property.dto.PropertySummaryResponseDTO;
import com.upcraft.stay.property.dto.UpdatePropertyRequestDTO;
import com.upcraft.stay.property.service.PropertyService;

/**
 * Property directory, configuration, archival, and occupancy summary controller.
 */
@RestController
@RequestMapping("/api/v1/properties")
@RequiredArgsConstructor
public class PropertyV1Controller {

    private final PropertyService propertyService;

    /**
     * Lists properties accessible to the authenticated owner or manager.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('PG_OWNER', 'MANAGER')")
    public ResponseEntity<ApiResponseDTO<PageResponseDTO<PropertySummaryResponseDTO>>> listProperties(
            Principal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection
    ) {
        PageResponseDTO<PropertySummaryResponseDTO> propertyPage = propertyService.listAccessibleProperties(
                principal.getName(), page, size, sortBy, sortDirection
        );
        return ResponseEntity.ok(ApiResponseDTO.success("Properties loaded", propertyPage, traceId()));
    }

    /**
     * Fetches details for a single property after verifying access rights.
     */
    @GetMapping("/{propertyId}")
    @PreAuthorize("@accessGuard.canAccessProperty(#propertyId)")
    public ResponseEntity<ApiResponseDTO<PropertySummaryResponseDTO>> getProperty(
            Principal principal,
            @PathVariable UUID propertyId
    ) {
        PropertySummaryResponseDTO propertyDTO = propertyService.getPropertyById(principal.getName(), propertyId);
        return ResponseEntity.ok(ApiResponseDTO.success("Property loaded", propertyDTO, traceId()));
    }

    /**
     * Returns live database-backed occupancy, bed breakdown, and policy summary for a property.
     */
    @GetMapping("/{propertyId}/summary")
    @PreAuthorize("@accessGuard.canAccessProperty(#propertyId)")
    public ResponseEntity<ApiResponseDTO<PropertyOccupancySummaryResponseDTO>> getPropertySummary(
            Principal principal,
            @PathVariable UUID propertyId
    ) {
        PropertyOccupancySummaryResponseDTO summaryDTO = propertyService.getPropertyOccupancySummary(
                principal.getName(), propertyId
        );
        return ResponseEntity.ok(ApiResponseDTO.success("Property summary loaded", summaryDTO, traceId()));
    }

    /**
     * Creates a new PG property inside the owner's organization.
     */
    @PostMapping
    @PreAuthorize("hasRole('PG_OWNER')")
    public ResponseEntity<ApiResponseDTO<PropertySummaryResponseDTO>> createProperty(
            Principal principal,
            @Valid @RequestBody CreatePropertyRequestDTO createPropertyRequestDTO
    ) {
        PropertySummaryResponseDTO propertyDTO = propertyService.createProperty(
                principal.getName(), createPropertyRequestDTO
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseDTO.success("Property created successfully", propertyDTO, traceId()));
    }

    /**
     * Updates property address, house rules, rent policy, and default electricity tariff.
     */
    @PutMapping("/{propertyId}")
    @PreAuthorize("hasRole('PG_OWNER')")
    public ResponseEntity<ApiResponseDTO<PropertySummaryResponseDTO>> updateProperty(
            Principal principal,
            @PathVariable UUID propertyId,
            @Valid @RequestBody UpdatePropertyRequestDTO updatePropertyRequestDTO
    ) {
        PropertySummaryResponseDTO propertyDTO = propertyService.updateProperty(
                principal.getName(), propertyId, updatePropertyRequestDTO
        );
        return ResponseEntity.ok(ApiResponseDTO.success("Property updated successfully", propertyDTO, traceId()));
    }

    /**
     * Archives a property only if no active, notice-period, or reserved tenancies exist.
     */
    @PatchMapping("/{propertyId}/archive")
    @PreAuthorize("hasRole('PG_OWNER')")
    public ResponseEntity<ApiResponseDTO<PropertySummaryResponseDTO>> archiveProperty(
            Principal principal,
            @PathVariable UUID propertyId
    ) {
        PropertySummaryResponseDTO propertyDTO = propertyService.archiveProperty(principal.getName(), propertyId);
        return ResponseEntity.ok(ApiResponseDTO.success("Property archived successfully", propertyDTO, traceId()));
    }

    private String traceId() {
        return MDC.get(AppConstants.TRACE_ID_MDC_KEY);
    }
}
