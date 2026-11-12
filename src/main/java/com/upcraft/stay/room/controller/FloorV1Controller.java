package com.upcraft.stay.room.controller;

import java.security.Principal;
import java.util.UUID;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
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
import com.upcraft.stay.room.dto.CreateFloorRequestDTO;
import com.upcraft.stay.room.dto.FloorResponseDTO;
import com.upcraft.stay.room.dto.UpdateFloorRequestDTO;
import com.upcraft.stay.room.service.FloorService;

/**
 * Property-scoped floor hierarchy controller.
 */
@RestController
@RequestMapping("/api/v1/properties/{propertyId}/floors")
@RequiredArgsConstructor
public class FloorV1Controller {

    private final FloorService floorService;

    @GetMapping
    @PreAuthorize("@accessGuard.canAccessProperty(#propertyId)")
    public ResponseEntity<ApiResponseDTO<PageResponseDTO<FloorResponseDTO>>> listFloors(
            Principal principal,
            @PathVariable UUID propertyId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size,
            @RequestParam(defaultValue = "floorNumber") String sortBy,
            @RequestParam(defaultValue = "ASC") String sortDirection
    ) {
        PageResponseDTO<FloorResponseDTO> floorPage = floorService.listFloors(
                principal.getName(), propertyId, page, size, sortBy, sortDirection
        );
        return ResponseEntity.ok(ApiResponseDTO.success("Floors loaded", floorPage, traceId()));
    }

    @PostMapping
    @PreAuthorize("hasRole('PG_OWNER')")
    public ResponseEntity<ApiResponseDTO<FloorResponseDTO>> createFloor(
            Principal principal,
            @PathVariable UUID propertyId,
            @Valid @RequestBody CreateFloorRequestDTO createFloorRequestDTO
    ) {
        FloorResponseDTO floorDTO = floorService.createFloor(principal.getName(), propertyId, createFloorRequestDTO);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseDTO.success("Floor created", floorDTO, traceId()));
    }

    @PutMapping("/{floorId}")
    @PreAuthorize("hasRole('PG_OWNER')")
    public ResponseEntity<ApiResponseDTO<FloorResponseDTO>> updateFloor(
            Principal principal,
            @PathVariable UUID propertyId,
            @PathVariable UUID floorId,
            @Valid @RequestBody UpdateFloorRequestDTO updateFloorRequestDTO
    ) {
        FloorResponseDTO floorDTO = floorService.updateFloor(
                principal.getName(), propertyId, floorId, updateFloorRequestDTO
        );
        return ResponseEntity.ok(ApiResponseDTO.success("Floor updated", floorDTO, traceId()));
    }

    @DeleteMapping("/{floorId}")
    @PreAuthorize("hasRole('PG_OWNER')")
    public ResponseEntity<Void> deleteFloor(
            Principal principal,
            @PathVariable UUID propertyId,
            @PathVariable UUID floorId
    ) {
        floorService.deleteFloor(principal.getName(), propertyId, floorId);
        return ResponseEntity.noContent().build();
    }

    private String traceId() {
        return MDC.get(AppConstants.TRACE_ID_MDC_KEY);
    }
}

