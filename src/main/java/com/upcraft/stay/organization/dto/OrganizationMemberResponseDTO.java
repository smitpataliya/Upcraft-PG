package com.upcraft.stay.organization.dto;

import java.time.Instant;
import java.util.UUID;

import com.upcraft.stay.domain.enums.Role;

/**
 * Organization member summary projection.
 */
public record OrganizationMemberResponseDTO(
        UUID id,
        UUID userId,
        String name,
        String email,
        String phone,
        Role memberRole,
        boolean active,
        boolean primary,
        Instant createdAt
) {}

