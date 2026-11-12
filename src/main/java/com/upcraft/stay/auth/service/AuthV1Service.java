package com.upcraft.stay.auth.service;

import com.upcraft.stay.auth.dto.AuthTokenResponseDTO;
import com.upcraft.stay.auth.dto.AuthenticatedUserDTO;
import com.upcraft.stay.auth.dto.ForgotPasswordRequestDTO;
import com.upcraft.stay.auth.dto.LoginRequestDTO;
import com.upcraft.stay.auth.dto.RegisterOwnerRequestDTO;
import com.upcraft.stay.auth.dto.ResetPasswordRequestDTO;
import com.upcraft.stay.auth.dto.VerifyOtpRequestDTO;
import com.upcraft.stay.auth.dto.VerifyOtpResponseDTO;

/**
 * Versioned v1 authentication, owner onboarding, and session context service.
 */
public interface AuthV1Service {

    AuthTokenResponseDTO registerOwner(RegisterOwnerRequestDTO registerOwnerRequestDTO);

    AuthTokenResponseDTO login(LoginRequestDTO loginRequestDTO);

    AuthenticatedUserDTO getCurrentUser(String principalEmail);

    void forgotPassword(ForgotPasswordRequestDTO forgotPasswordRequestDTO);

    VerifyOtpResponseDTO verifyOtp(VerifyOtpRequestDTO verifyOtpRequestDTO);

    void resetPassword(ResetPasswordRequestDTO resetPasswordRequestDTO);
}

