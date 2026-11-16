package com.upcraft.stay.reporting.controller;

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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.upcraft.stay.common.constants.AppConstants;
import com.upcraft.stay.common.dto.ApiResponseDTO;
import com.upcraft.stay.common.dto.PageResponseDTO;
import com.upcraft.stay.reporting.dto.CreateShareLinkRequestDTO;
import com.upcraft.stay.reporting.dto.ShareLinkResponseDTO;
import com.upcraft.stay.reporting.service.ShareLinkV1Service;

/**
 * Versioned REST controller for creating, listing, and revoking expiring share links.
 */
@RestController
@RequestMapping("/api/v1/share-links")
@RequiredArgsConstructor
public class ShareLinkV1Controller {

    private final ShareLinkV1Service shareLinkV1Service;

    @PostMapping
    @PreAuthorize("hasAnyRole('PG_OWNER', 'MANAGER', 'PG_MEMBER')")
    public ResponseEntity<ApiResponseDTO<ShareLinkResponseDTO>> createShareLink(
            Principal principal,
            @Valid @RequestBody CreateShareLinkRequestDTO requestDTO
    ) {
        ShareLinkResponseDTO created = shareLinkV1Service.createShareLink(principal.getName(), requestDTO);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseDTO.success("Share link created", created, traceId()));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('PG_OWNER', 'MANAGER')")
    public ResponseEntity<ApiResponseDTO<PageResponseDTO<ShareLinkResponseDTO>>> listShareLinks(
            Principal principal,
            @RequestParam(required = false) UUID propertyId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size
    ) {
        PageResponseDTO<ShareLinkResponseDTO> links = shareLinkV1Service.listShareLinks(
                principal.getName(), propertyId, page, size
        );
        return ResponseEntity.ok(ApiResponseDTO.success("Share links loaded", links, traceId()));
    }

    @DeleteMapping("/{shareLinkId}")
    @PreAuthorize("hasAnyRole('PG_OWNER', 'MANAGER', 'PG_MEMBER')")
    public ResponseEntity<ApiResponseDTO<ShareLinkResponseDTO>> revokeShareLink(
            Principal principal,
            @PathVariable UUID shareLinkId
    ) {
        ShareLinkResponseDTO revoked = shareLinkV1Service.revokeShareLink(principal.getName(), shareLinkId);
        return ResponseEntity.ok(ApiResponseDTO.success("Share link revoked", revoked, traceId()));
    }

    private String traceId() {
        return MDC.get(AppConstants.TRACE_ID_MDC_KEY);
    }
}

