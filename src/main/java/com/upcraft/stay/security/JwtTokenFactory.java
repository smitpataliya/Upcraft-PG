package com.upcraft.stay.security;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import com.upcraft.stay.auth.dto.AuthTokenResponseDTO;
import com.upcraft.stay.auth.dto.AuthenticatedUserDTO;
import com.upcraft.stay.common.config.AppProperties;
import com.upcraft.stay.common.constants.AppConstants;

/**
 * Shared JWT token builder producing v1 auth token responses without duplicating UserDetails wiring.
 */
@Component
@RequiredArgsConstructor
public class JwtTokenFactory {

    private final JwtUtil jwtUtil;
    private final AppProperties appProperties;

    /**
     * Generates a signed JWT and returns a complete {@link AuthTokenResponseDTO}.
     *
     * @param authenticatedUserDTO resolved user context
     * @param encodedPassword      stored password hash (may be blank for SSO accounts)
     * @param active               account enabled state
     * @return token response DTO
     */
    public AuthTokenResponseDTO createTokenResponse(
            AuthenticatedUserDTO authenticatedUserDTO,
            String encodedPassword,
            boolean active
    ) {
        UserDetails userDetails = new User(
                authenticatedUserDTO.email(),
                StringUtils.defaultString(encodedPassword),
                active,
                true,
                true,
                true,
                List.of(new SimpleGrantedAuthority(AppConstants.ROLE_PREFIX + authenticatedUserDTO.role().name()))
        );
        String token = jwtUtil.generateToken(userDetails);
        return new AuthTokenResponseDTO(
                token,
                "Bearer",
                appProperties.getJwt().getExpirationMs(),
                authenticatedUserDTO
        );
    }
}

