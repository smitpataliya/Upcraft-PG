package com.upcraft.stay.controller;

import com.upcraft.stay.domain.entity.User;
import com.upcraft.stay.dto.AuthRequest;
import com.upcraft.stay.dto.AuthResponse;
import com.upcraft.stay.dto.GoogleAuthRequest;
import com.upcraft.stay.repository.UserRepository;
import com.upcraft.stay.security.JwtUtil;
import com.upcraft.stay.service.AuthService;
import com.upcraft.stay.service.GoogleAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final GoogleAuthService googleAuthService;
    private final UserRepository userRepository;
    private final com.upcraft.stay.service.OtpService otpService;

    /** Standard email + password login (all roles) */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /** Google SSO login — PG_MEMBER only */
    @PostMapping("/google")
    public ResponseEntity<AuthResponse> googleLogin(@Valid @RequestBody GoogleAuthRequest request) {
        return ResponseEntity.ok(googleAuthService.loginWithGoogle(request.getIdToken()));
    }

    /** Forgot password — send OTP */
    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody com.upcraft.stay.dto.ForgotPasswordRequest request) {
        otpService.sendOtp(request.getEmail());
        return ResponseEntity.ok().build();
    }

    /** Verify OTP — returns reset token */
    @PostMapping("/verify-otp")
    public ResponseEntity<com.upcraft.stay.dto.VerifyOtpResponse> verifyOtp(@Valid @RequestBody com.upcraft.stay.dto.VerifyOtpRequest request) {
        try {
            String token = otpService.verifyOtp(request.getEmail(), request.getOtp());
            return ResponseEntity.ok(com.upcraft.stay.dto.VerifyOtpResponse.builder()
                    .resetToken(token)
                    .message("OTP verified successfully")
                    .build());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    /** Reset password using reset token */
    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody com.upcraft.stay.dto.ResetPasswordWithTokenRequest request) {
        try {
            otpService.resetPasswordWithToken(request.getResetToken(), request.getNewPassword());
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    /** Restore session — returns current user from JWT, no re-auth needed */
    @GetMapping("/me")
    public ResponseEntity<AuthResponse> me(Principal principal) {
        User user = userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return ResponseEntity.ok(AuthResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole())
                .phone(user.getPhone())
                .avatarUrl(user.getAvatarUrl())
                .build());
    }

    /** Logout endpoint */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.ok().build();
    }
}
