package com.upcraft.stay.organization.service;

import java.util.UUID;

import com.upcraft.stay.auth.dto.AuthTokenResponseDTO;
import com.upcraft.stay.common.dto.PageResponseDTO;
import com.upcraft.stay.organization.dto.AcceptInvitationRequestDTO;
import com.upcraft.stay.organization.dto.CreateInvitationRequestDTO;
import com.upcraft.stay.organization.dto.InvitationPreviewResponseDTO;
import com.upcraft.stay.organization.dto.InvitationResponseDTO;

/**
 * Invitation creation, validation, acceptance, and revocation service.
 */
public interface InvitationService {

    InvitationResponseDTO createInvitation(
            String principalEmail,
            CreateInvitationRequestDTO createInvitationRequestDTO
    );

    PageResponseDTO<InvitationResponseDTO> listInvitations(
            String principalEmail,
            UUID propertyId,
            int page,
            Integer size,
            String sortBy,
            String sortDirection
    );

    InvitationPreviewResponseDTO verifyInvitation(String token, String code);

    AuthTokenResponseDTO acceptInvitation(AcceptInvitationRequestDTO acceptInvitationRequestDTO);

    void revokeInvitation(String principalEmail, UUID invitationId);
}

