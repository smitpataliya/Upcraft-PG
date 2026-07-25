package com.upcraft.stay.service;

import com.upcraft.stay.domain.entity.User;
import com.upcraft.stay.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OtpService {

    private final OtpStore otpStore;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Autowired(required = false)
    private JavaMailSender mailSender;

    public void sendOtp(String email) {
        Optional<User> userOpt = userRepository.findByEmail(email.toLowerCase());
        if (userOpt.isEmpty()) {
            log.info("Password reset requested for non-existent email: {}", email);
            return;
        }

        String otp = otpStore.generateOtp(email);
        log.info("OTP generated for {}: {}", email, otp);

        if (mailSender != null) {
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setTo(email);
                message.setSubject("Shree Krishna PG - Password Reset OTP");
                message.setText("Hello,\n\nYour OTP for password reset is: " + otp
                        + "\n\nThis OTP is valid for 10 minutes."
                        + " If you did not request this, please ignore this email."
                        + "\n\nRegards,\nShree Krishna PG Team");
                mailSender.send(message);
                log.info("OTP email sent successfully to {}", email);
            } catch (Exception e) {
                log.error("Failed to send OTP email to {}: {}", email, e.getMessage());
            }
        } else {
            log.warn("JavaMailSender not configured. OTP for {} -> {}", email, otp);
        }
    }

    public String verifyOtp(String email, String otp) {
        boolean valid = otpStore.verifyOtp(email, otp);
        if (!valid) {
            throw new IllegalArgumentException("Invalid or expired OTP");
        }
        return otpStore.createResetToken(email);
    }

    public void resetPasswordWithToken(String resetToken, String newPassword) {
        String email = otpStore.getEmailByResetToken(resetToken);
        if (email == null) {
            throw new IllegalArgumentException("Invalid or expired reset session");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        otpStore.removeResetToken(resetToken);
        log.info("Password reset successful for user {}", email);
    }
}
