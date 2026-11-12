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

import com.upcraft.stay.common.constants.AppConstants;
import com.upcraft.stay.common.dto.ApiResponseDTO;
import com.upcraft.stay.common.dto.PageResponseDTO;
import com.upcraft.stay.organization.dto.AssignManagerRequestDTO;
import com.upcraft.stay.organization.dto.ManagerAssignmentResponseDTO;
import com.upcraft.stay.organization.dto.OrganizationMemberResponseDTO;
import com.upcraft.stay.organization.dto.OrganizationResponseDTO;
import com.upcraft.stay.organization.service.OrganizationService;

/**
 * Organization profile, member directory, and manager property assignment REST controller.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class OrganizationV1Controller {

    private final OrganizationService organizationService;

    /**
     * Returns the current user's active organization profile.
     */
    @GetMapping("/organizations/current")
    public ResponseEntity<ApiResponseDTO<OrganizationResponseDTO>> getCurrentOrganization(Principal principal) {
        OrganizationResponseDTO organizationDTO = organizationService.getCurrentOrganization(principal.getName());
        return ResponseEntity.ok(ApiResponseDTO.success("Organization loaded", organizationDTO, traceId()));
    }

    /**
     * Lists paginated members of the owner's organization.
     */
    @GetMapping("/organizations/members")
    @PreAuthorize("hasRole('PG_OWNER')")
    public ResponseEntity<ApiResponseDTO<PageResponseDTO<OrganizationMemberResponseDTO>>> getOrganizationMembers(
            Principal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection
    ) {
        PageResponseDTO<OrganizationMemberResponseDTO> membersPage = organizationService.getOrganizationMembers(
                principal.getName(), page, size, sortBy, sortDirection
        );
        return ResponseEntity.ok(ApiResponseDTO.success("Organization members loaded", membersPage, traceId()));
    }

    /**
     * Lists paginated manager-to-property assignments for the owner's organization.
     */
    @GetMapping("/managers/assignments")
    @PreAuthorize("hasRole('PG_OWNER')")
    public ResponseEntity<ApiResponseDTO<PageResponseDTO<ManagerAssignmentResponseDTO>>> getManagerAssignments(
            Principal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection
    ) {
        PageResponseDTO<ManagerAssignmentResponseDTO> assignmentsPage = organizationService.getManagerAssignments(
                principal.getName(), page, size, sortBy, sortDirection
        );
        return ResponseEntity.ok(ApiResponseDTO.success("Manager assignments loaded", assignmentsPage, traceId()));
    }

    /**
     * Assigns or updates manager permissions for a property in the owner's organization.
     */
    @PostMapping("/managers/assignments")
    @PreAuthorize("hasRole('PG_OWNER')")
    public ResponseEntity<ApiResponseDTO<ManagerAssignmentResponseDTO>> assignManager(
            Principal principal,
            @Valid @RequestBody AssignManagerRequestDTO assignManagerRequestDTO
    ) {
        ManagerAssignmentResponseDTO assignmentDTO = organizationService.assignManagerToProperty(
                principal.getName(), assignManagerRequestDTO
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseDTO.success("Manager assigned to property", assignmentDTO, traceId()));
    }

    /**
     * Soft-revokes a manager property assignment.
     */
    @DeleteMapping("/managers/assignments/{assignmentId}")
    @PreAuthorize("hasRole('PG_OWNER')")
    public ResponseEntity<ApiResponseDTO<Void>> revokeManagerAssignment(
            Principal principal,
            @PathVariable UUID assignmentId
    ) {
        organizationService.revokeManagerAssignment(principal.getName(), assignmentId);
        return ResponseEntity.ok(ApiResponseDTO.success("Manager assignment revoked", null, traceId()));
    }

    private String traceId() {
        return MDC.get(AppConstants.TRACE_ID_MDC_KEY);
    }
}

