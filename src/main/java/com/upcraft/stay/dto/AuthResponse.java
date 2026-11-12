package com.upcraft.stay.dto;

import java.util.UUID;

import lombok.Builder;
import lombok.Data;

import com.upcraft.stay.auth.dto.AuthenticatedUserDTO;
import com.upcraft.stay.domain.enums.Role;

/**
 * Legacy authentication response preserving flat properties alongside nested {@code user} metadata.
 */
@Data
@Builder
public class AuthResponse {
    private String token;
    private String type;
    private UUID id;
    private String name;
    private String email;
    private String username;
    private Role role;
    private String phone;
    private String avatarUrl;
    private AuthenticatedUserDTO user;
}
