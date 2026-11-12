package com.upcraft.stay.organization.service;

import java.util.UUID;

import com.upcraft.stay.common.dto.PageResponseDTO;
import com.upcraft.stay.organization.dto.AssignManagerRequestDTO;
import com.upcraft.stay.organization.dto.ManagerAssignmentResponseDTO;
import com.upcraft.stay.organization.dto.OrganizationMemberResponseDTO;
import com.upcraft.stay.organization.dto.OrganizationResponseDTO;

/**
 * Organization profile, member directory, and manager property-assignment service.
 */
public interface OrganizationService {

    OrganizationResponseDTO getCurrentOrganization(String principalEmail);

    PageResponseDTO<OrganizationMemberResponseDTO> getOrganizationMembers(
            String principalEmail,
            int page,
            Integer size,
            String sortBy,
            String sortDirection
    );

    ManagerAssignmentResponseDTO assignManagerToProperty(
            String principalEmail,
            AssignManagerRequestDTO assignManagerRequestDTO
    );

    PageResponseDTO<ManagerAssignmentResponseDTO> getManagerAssignments(
            String principalEmail,
            int page,
            Integer size,
            String sortBy,
            String sortDirection
    );

    void revokeManagerAssignment(String principalEmail, UUID assignmentId);
}

