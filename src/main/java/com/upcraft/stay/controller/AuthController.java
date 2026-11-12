package com.upcraft.stay.controller;

import java.security.Principal;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.upcraft.stay.auth.dto.AuthenticatedUserDTO;
import com.upcraft.stay.domain.entity.User;
import com.upcraft.stay.dto.AuthRequest;
import com.upcraft.stay.dto.AuthResponse;
import com.upcraft.stay.dto.ForgotPasswordRequest;
import com.upcraft.stay.dto.GoogleAuthRequest;
import com.upcraft.stay.dto.ResetPasswordWithTokenRequest;
import com.upcraft.stay.dto.VerifyOtpRequest;
import com.upcraft.stay.dto.VerifyOtpResponse;
import com.upcraft.stay.repository.UserRepository;
import com.upcraft.stay.security.AccessGuardService;
import com.upcraft.stay.service.AuthService;
import com.upcraft.stay.service.GoogleAuthService;
import com.upcraft.stay.service.OtpService;

/**
 * Legacy authentication controller preserved for backward compatibility across existing web clients.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final GoogleAuthService googleAuthService;
    private final UserRepository userRepository;
    private final OtpService otpService;
    private final AccessGuardService accessGuardService;

    /** Standard email + password login (all roles). */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /** Google SSO login — PG_MEMBER only. */
    @PostMapping("/google")
    public ResponseEntity<AuthResponse> googleLogin(@Valid @RequestBody GoogleAuthRequest request) {
        return ResponseEntity.ok(googleAuthService.loginWithGoogle(request.getIdToken()));
    }

    /** Forgot password — send OTP. */
    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        otpService.sendOtp(request.getEmail());
        return ResponseEntity.ok().build();
    }

    /** Verify OTP — returns reset token. */
    @PostMapping("/verify-otp")
    public ResponseEntity<VerifyOtpResponse> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        try {
            String token = otpService.verifyOtp(request.getEmail(), request.getOtp());
            return ResponseEntity.ok(VerifyOtpResponse.builder()
                    .resetToken(token)
                    .message("OTP verified successfully")
                    .build());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }

    /** Reset password using reset token. */
    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordWithTokenRequest request) {
        try {
            otpService.resetPasswordWithToken(request.getResetToken(), request.getNewPassword());
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }

    /** Restore session — returns current user from JWT, no re-auth needed. */
    @GetMapping("/me")
    public ResponseEntity<AuthResponse> me(Principal principal) {
        User userEntity = userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        AuthenticatedUserDTO authenticatedUserDTO = accessGuardService.buildAuthenticatedUser(userEntity);
        return ResponseEntity.ok(AuthResponse.builder()
                .id(userEntity.getId())
                .name(userEntity.getName())
                .email(userEntity.getEmail())
                .username(userEntity.getUsername())
                .role(userEntity.getRole())
                .phone(userEntity.getPhone())
                .avatarUrl(userEntity.getAvatarUrl())
                .user(authenticatedUserDTO)
                .build());
    }

    /** Logout endpoint. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.ok().build();
    }
}
