package com.upcraft.stay.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.upcraft.stay.domain.entity.User;
import com.upcraft.stay.domain.enums.Role;
import com.upcraft.stay.dto.AuthResponse;
import com.upcraft.stay.repository.UserRepository;
import com.upcraft.stay.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Verifies a Google ID token via Google's tokeninfo endpoint (no extra dependency needed).
 * After verification, finds or creates the member account, then returns a JWT.
 *
 * NOTE: Only PG_MEMBER accounts can use Google SSO.
 *       PG_OWNER and SUPER_ADMIN must log in with email + password.
 */
@Service
@RequiredArgsConstructor
public class GoogleAuthService {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.google.client-id:}")
    private String googleClientId;

    @Transactional
    public AuthResponse loginWithGoogle(String idToken) {
        // Verify token with Google
        GooglePayload payload = verifyGoogleToken(idToken);

        // Find or create user
        User user = userRepository.findByEmail(payload.email())
                .orElseGet(() -> createGoogleUser(payload));

        // Only PG_MEMBER can use Google SSO
        if (user.getRole() != Role.PG_MEMBER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Google SSO is only available for PG Members. Please use email/password login.");
        }

        if (!user.isActive()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Your account has been deactivated. Please contact the owner.");
        }

        // Update Google ID and avatar if changed
        if (!payload.googleId().equals(user.getGoogleId())) {
            user.setGoogleId(payload.googleId());
        }
        if (payload.picture() != null && !payload.picture().equals(user.getAvatarUrl())) {
            user.setAvatarUrl(payload.picture());
        }
        userRepository.save(user);

        // Generate JWT (same mechanism as password login)
        var userDetails = org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPassword() != null ? user.getPassword() : "")
                .roles(user.getRole().name())
                .build();

        String token = jwtUtil.generateToken(userDetails);

        return AuthResponse.builder()
                .token(token)
                .type("Bearer")
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole())
                .phone(user.getPhone())
                .avatarUrl(user.getAvatarUrl())
                .build();
    }

    private User createGoogleUser(GooglePayload payload) {
        // New SSO user — role starts as PG_MEMBER.
        // The PG_OWNER must subsequently assign them a room.
        return userRepository.save(User.builder()
                .name(payload.name())
                .email(payload.email())
                .password(null)           // No password for SSO-only users
                .googleId(payload.googleId())
                .avatarUrl(payload.picture())
                .role(Role.PG_MEMBER)
                .active(true)
                .build());
    }

    /**
     * Calls Google's tokeninfo endpoint to validate the ID token.
     * Returns the extracted payload on success.
     */
    private GooglePayload verifyGoogleToken(String idToken) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://oauth2.googleapis.com/tokeninfo?id_token=" + idToken))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Google token");
            }

            JsonNode json = objectMapper.readTree(response.body());

            // Validate audience (aud) matches our client ID if configured
            if (!googleClientId.isBlank()) {
                String aud = json.path("aud").asText();
                if (!googleClientId.equals(aud)) {
                    throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token audience mismatch");
                }
            }

            return new GooglePayload(
                    json.path("sub").asText(),
                    json.path("email").asText(),
                    json.path("name").asText("Unknown"),
                    json.path("picture").asText(null)
            );

        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Failed to verify Google token: " + e.getMessage());
        }
    }

    record GooglePayload(String googleId, String email, String name, String picture) {}
}
