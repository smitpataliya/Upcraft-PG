package com.upcraft.stay.auth.dto;

import java.util.List;
import java.util.UUID;

import com.upcraft.stay.domain.enums.Role;

/**
 * Authenticated user projection carrying role, organization, and assigned property scopes.
 */
public record AuthenticatedUserDTO(
        UUID id,
        String name,
        String email,
        String username,
        Role role,
        String phone,
        String avatarUrl,
        UUID organizationId,
        String organizationName,
        List<UUID> assignedPropertyIds
) {}

