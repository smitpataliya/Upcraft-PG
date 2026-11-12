package com.upcraft.stay.organization.controller;

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

import com.upcraft.stay.auth.dto.AuthTokenResponseDTO;
import com.upcraft.stay.common.constants.AppConstants;
import com.upcraft.stay.common.dto.ApiResponseDTO;
import com.upcraft.stay.common.dto.PageResponseDTO;
import com.upcraft.stay.organization.dto.AcceptInvitationRequestDTO;
import com.upcraft.stay.organization.dto.CreateInvitationRequestDTO;
import com.upcraft.stay.organization.dto.InvitationPreviewResponseDTO;
import com.upcraft.stay.organization.dto.InvitationResponseDTO;
import com.upcraft.stay.organization.service.InvitationService;

/**
 * Invitation onboarding REST controller for managers and tenants.
 */
@RestController
@RequestMapping("/api/v1/invitations")
@RequiredArgsConstructor
public class InvitationV1Controller {

    private final InvitationService invitationService;

    /**
     * Creates an invitation link and one-time code for a manager or tenant.
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('PG_OWNER', 'MANAGER')")
    public ResponseEntity<ApiResponseDTO<InvitationResponseDTO>> createInvitation(
            Principal principal,
            @Valid @RequestBody CreateInvitationRequestDTO createInvitationRequestDTO
    ) {
        InvitationResponseDTO invitationDTO = invitationService.createInvitation(
                principal.getName(), createInvitationRequestDTO
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseDTO.success("Invitation created", invitationDTO, traceId()));
    }

    /**
     * Lists paginated invitations scoped by organization or assigned property.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('PG_OWNER', 'MANAGER')")
    public ResponseEntity<ApiResponseDTO<PageResponseDTO<InvitationResponseDTO>>> listInvitations(
            Principal principal,
            @RequestParam(required = false) UUID propertyId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection
    ) {
        PageResponseDTO<InvitationResponseDTO> invitationsPage = invitationService.listInvitations(
                principal.getName(), propertyId, page, size, sortBy, sortDirection
        );
        return ResponseEntity.ok(ApiResponseDTO.success("Invitations loaded", invitationsPage, traceId()));
    }

    /**
     * Publicly verifies an invitation token or one-time code and returns non-sensitive context.
     */
    @GetMapping("/verify")
    public ResponseEntity<ApiResponseDTO<InvitationPreviewResponseDTO>> verifyInvitation(
            @RequestParam(required = false) String token,
            @RequestParam(required = false) String code
    ) {
        InvitationPreviewResponseDTO previewDTO = invitationService.verifyInvitation(token, code);
        return ResponseEntity.ok(ApiResponseDTO.success("Invitation verified", previewDTO, traceId()));
    }

    /**
     * Accepts an active invitation and returns an authenticated JWT session.
     */
    @PostMapping("/accept")
    public ResponseEntity<ApiResponseDTO<AuthTokenResponseDTO>> acceptInvitation(
            @Valid @RequestBody AcceptInvitationRequestDTO acceptInvitationRequestDTO
    ) {
        AuthTokenResponseDTO tokenResponse = invitationService.acceptInvitation(acceptInvitationRequestDTO);
        return ResponseEntity.ok(ApiResponseDTO.success("Invitation accepted", tokenResponse, traceId()));
    }

    /**
     * Revokes a pending invitation.
     */
    @DeleteMapping("/{invitationId}")
    @PreAuthorize("hasAnyRole('PG_OWNER', 'MANAGER')")
    public ResponseEntity<ApiResponseDTO<Void>> revokeInvitation(
            Principal principal,
            @PathVariable UUID invitationId
    ) {
        invitationService.revokeInvitation(principal.getName(), invitationId);
        return ResponseEntity.ok(ApiResponseDTO.success("Invitation revoked", null, traceId()));
    }

    private String traceId() {
        return MDC.get(AppConstants.TRACE_ID_MDC_KEY);
    }
}

