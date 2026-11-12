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
import com.upcraft.stay.room.dto.CreatePropertyRoomRequestDTO;
import com.upcraft.stay.room.dto.PropertyRoomResponseDTO;
import com.upcraft.stay.room.dto.UpdatePropertyRoomRequestDTO;
import com.upcraft.stay.room.service.PropertyRoomService;

/**
 * Property-scoped room hierarchy controller.
 */
@RestController
@RequestMapping("/api/v1/properties/{propertyId}/rooms")
@RequiredArgsConstructor
public class RoomV1Controller {

    private final PropertyRoomService propertyRoomService;

    @GetMapping
    @PreAuthorize("@accessGuard.canAccessProperty(#propertyId)")
    public ResponseEntity<ApiResponseDTO<PageResponseDTO<PropertyRoomResponseDTO>>> listRooms(
            Principal principal,
            @PathVariable UUID propertyId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size,
            @RequestParam(defaultValue = "number") String sortBy,
            @RequestParam(defaultValue = "ASC") String sortDirection
    ) {
        PageResponseDTO<PropertyRoomResponseDTO> roomPage = propertyRoomService.listPropertyRooms(
                principal.getName(), propertyId, page, size, sortBy, sortDirection
        );
        return ResponseEntity.ok(ApiResponseDTO.success("Rooms loaded", roomPage, traceId()));
    }

    @GetMapping("/{roomId}")
    @PreAuthorize("@accessGuard.canAccessProperty(#propertyId)")
    public ResponseEntity<ApiResponseDTO<PropertyRoomResponseDTO>> getRoom(
            Principal principal,
            @PathVariable UUID propertyId,
            @PathVariable UUID roomId
    ) {
        PropertyRoomResponseDTO roomDTO = propertyRoomService.getPropertyRoom(principal.getName(), propertyId, roomId);
        return ResponseEntity.ok(ApiResponseDTO.success("Room loaded", roomDTO, traceId()));
    }

    @PostMapping
    @PreAuthorize("hasRole('PG_OWNER')")
    public ResponseEntity<ApiResponseDTO<PropertyRoomResponseDTO>> createRoom(
            Principal principal,
            @PathVariable UUID propertyId,
            @Valid @RequestBody CreatePropertyRoomRequestDTO createRoomRequestDTO
    ) {
        PropertyRoomResponseDTO roomDTO = propertyRoomService.createPropertyRoom(
                principal.getName(), propertyId, createRoomRequestDTO
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseDTO.success("Room created", roomDTO, traceId()));
    }

    @PutMapping("/{roomId}")
    @PreAuthorize("hasRole('PG_OWNER')")
    public ResponseEntity<ApiResponseDTO<PropertyRoomResponseDTO>> updateRoom(
            Principal principal,
            @PathVariable UUID propertyId,
            @PathVariable UUID roomId,
            @Valid @RequestBody UpdatePropertyRoomRequestDTO updateRoomRequestDTO
    ) {
        PropertyRoomResponseDTO roomDTO = propertyRoomService.updatePropertyRoom(
                principal.getName(), propertyId, roomId, updateRoomRequestDTO
        );
        return ResponseEntity.ok(ApiResponseDTO.success("Room updated", roomDTO, traceId()));
    }

    @DeleteMapping("/{roomId}")
    @PreAuthorize("hasRole('PG_OWNER')")
    public ResponseEntity<Void> deleteRoom(
            Principal principal,
            @PathVariable UUID propertyId,
            @PathVariable UUID roomId
    ) {
        propertyRoomService.deletePropertyRoom(principal.getName(), propertyId, roomId);
        return ResponseEntity.noContent().build();
    }

    private String traceId() {
        return MDC.get(AppConstants.TRACE_ID_MDC_KEY);
    }
}

