package com.upcraft.stay.organization.dto;

import java.time.Instant;

import com.upcraft.stay.common.enums.InvitationStatus;
import com.upcraft.stay.domain.enums.Role;

/**
 * Public-safe preview of an invitation before acceptance.
 */
public record InvitationPreviewResponseDTO(
        String organizationName,
        String propertyName,
        String inviteeName,
        String email,
        Role targetRole,
        InvitationStatus invitationStatus,
        Instant expiresAt,
        boolean valid
) {}

