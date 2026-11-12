package com.upcraft.stay.organization.dto;

import java.time.Instant;
import java.util.UUID;

import com.upcraft.stay.common.enums.InvitationStatus;
import com.upcraft.stay.domain.enums.Role;

/**
 * Invitation summary returned upon creation or listing.
 * Plaintext {@code inviteUrl} and {@code inviteCode} are populated only on creation.
 */
public record InvitationResponseDTO(
        UUID id,
        UUID organizationId,
        UUID propertyId,
        String propertyName,
        String email,
        String phone,
        String inviteeName,
        Role targetRole,
        InvitationStatus invitationStatus,
        Instant expiresAt,
        Instant acceptedAt,
        String inviteUrl,
        String inviteCode,
        Instant createdAt
) {}

