package com.upcraft.stay.auth.controller;

import java.security.Principal;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.upcraft.stay.auth.dto.AuthTokenResponseDTO;
import com.upcraft.stay.auth.dto.AuthenticatedUserDTO;
import com.upcraft.stay.auth.dto.ForgotPasswordRequestDTO;
import com.upcraft.stay.auth.dto.LoginRequestDTO;
import com.upcraft.stay.auth.dto.RegisterOwnerRequestDTO;
import com.upcraft.stay.auth.dto.ResetPasswordRequestDTO;
import com.upcraft.stay.auth.dto.VerifyOtpRequestDTO;
import com.upcraft.stay.auth.dto.VerifyOtpResponseDTO;
import com.upcraft.stay.auth.service.AuthV1Service;
import com.upcraft.stay.common.constants.AppConstants;
import com.upcraft.stay.common.dto.ApiResponseDTO;

/**
 * Versioned v1 authentication, owner onboarding, and password recovery controller.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthV1Controller {

    private final AuthV1Service authV1Service;

    /**
     * Registers a new PG owner, default organization, and initial property.
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponseDTO<AuthTokenResponseDTO>> registerOwner(
            @Valid @RequestBody RegisterOwnerRequestDTO registerOwnerRequestDTO
    ) {
        AuthTokenResponseDTO tokenResponse = authV1Service.registerOwner(registerOwnerRequestDTO);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseDTO.success("Owner account created successfully", tokenResponse, traceId()));
    }

    /**
     * Authenticates an existing user across web, Android, and iOS clients.
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponseDTO<AuthTokenResponseDTO>> login(
            @Valid @RequestBody LoginRequestDTO loginRequestDTO
    ) {
        AuthTokenResponseDTO tokenResponse = authV1Service.login(loginRequestDTO);
        return ResponseEntity.ok(ApiResponseDTO.success("Signed in successfully", tokenResponse, traceId()));
    }

    /**
     * Returns the current authenticated user profile, organization, and assigned properties.
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponseDTO<AuthenticatedUserDTO>> getCurrentUser(Principal principal) {
        AuthenticatedUserDTO userDTO = authV1Service.getCurrentUser(principal.getName());
        return ResponseEntity.ok(ApiResponseDTO.success("Session restored", userDTO, traceId()));
    }

    /**
     * Initiates an OTP email for password reset.
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponseDTO<Void>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequestDTO forgotPasswordRequestDTO
    ) {
        authV1Service.forgotPassword(forgotPasswordRequestDTO);
        return ResponseEntity.ok(ApiResponseDTO.success(
                "If an account exists for that email, a reset OTP has been sent", null, traceId()
        ));
    }

    /**
     * Validates a password-reset OTP and returns a short-lived reset token.
     */
    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponseDTO<VerifyOtpResponseDTO>> verifyOtp(
            @Valid @RequestBody VerifyOtpRequestDTO verifyOtpRequestDTO
    ) {
        VerifyOtpResponseDTO responseDTO = authV1Service.verifyOtp(verifyOtpRequestDTO);
        return ResponseEntity.ok(ApiResponseDTO.success("OTP verified", responseDTO, traceId()));
    }

    /**
     * Updates the user password using a verified reset token.
     */
    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponseDTO<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequestDTO resetPasswordRequestDTO
    ) {
        authV1Service.resetPassword(resetPasswordRequestDTO);
        return ResponseEntity.ok(ApiResponseDTO.success("Password updated successfully", null, traceId()));
    }

    /**
     * Stateless JWT logout confirmation endpoint.
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponseDTO<Void>> logout() {
        return ResponseEntity.ok(ApiResponseDTO.success("Logged out successfully", null, traceId()));
    }

    private String traceId() {
        return MDC.get(AppConstants.TRACE_ID_MDC_KEY);
    }
}

