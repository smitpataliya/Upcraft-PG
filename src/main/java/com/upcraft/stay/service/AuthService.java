package com.upcraft.stay.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.upcraft.stay.auth.dto.AuthTokenResponseDTO;
import com.upcraft.stay.auth.dto.AuthenticatedUserDTO;
import com.upcraft.stay.domain.entity.User;
import com.upcraft.stay.dto.AuthRequest;
import com.upcraft.stay.dto.AuthResponse;
import com.upcraft.stay.repository.UserRepository;
import com.upcraft.stay.security.AccessGuardService;
import com.upcraft.stay.security.JwtTokenFactory;

/**
 * Legacy authentication service delegating token and context generation to v1 security components.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final AccessGuardService accessGuardService;
    private final JwtTokenFactory jwtTokenFactory;

    /**
     * Authenticates a user and returns both legacy flat fields and nested v1 user context.
     *
     * @param authRequest login payload
     * @return backward-compatible auth response
     */
    @Transactional(readOnly = true)
    public AuthResponse login(AuthRequest authRequest) {
        String identifier = authRequest.getEmail().trim().toLowerCase();
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(identifier, authRequest.getPassword())
        );

        User userEntity = userRepository.findByEmailOrUsername(identifier, identifier)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        AuthenticatedUserDTO authenticatedUserDTO = accessGuardService.buildAuthenticatedUser(userEntity);
        AuthTokenResponseDTO tokenResponse = jwtTokenFactory.createTokenResponse(
                authenticatedUserDTO, userEntity.getPassword(), userEntity.isActive()
        );

        return AuthResponse.builder()
                .token(tokenResponse.token())
                .type("Bearer")
                .id(userEntity.getId())
                .name(userEntity.getName())
                .email(userEntity.getEmail())
                .username(userEntity.getUsername())
                .role(userEntity.getRole())
                .phone(userEntity.getPhone())
                .avatarUrl(userEntity.getAvatarUrl())
                .user(authenticatedUserDTO)
                .build();
    }
}
