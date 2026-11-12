package com.upcraft.stay.auth.dto;

/**
 * Standard v1 authentication token and user context response.
 */
public record AuthTokenResponseDTO(
        String token,
        String tokenType,
        long expiresInMs,
        AuthenticatedUserDTO user
) {}

