package com.upcraft.stay.service;

import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.upcraft.stay.domain.entity.User;
import com.upcraft.stay.repository.UserRepository;

/**
 * Password-reset OTP generation, verification, and password update service.
 * Never logs OTP values or credentials.
 */
@Slf4j
@Service
public class OtpService {

    private final OtpStore otpStore;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Optional<JavaMailSender> mailSender;

    public OtpService(
            OtpStore otpStore,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Autowired(required = false) JavaMailSender mailSender
    ) {
        this.otpStore = otpStore;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailSender = Optional.ofNullable(mailSender);
    }

    /**
     * Generates a password-reset OTP and dispatches email when mail delivery is configured.
     *
     * @param email target account email
     */
    public void sendOtp(String email) {
        String normalizedEmail = StringUtils.trimToEmpty(email).toLowerCase();
        Optional<User> userOptional = userRepository.findByEmail(normalizedEmail);
        if (userOptional.isEmpty()) {
            log.info("Password reset requested for unregistered email.");
            return;
        }

        String otp = otpStore.generateOtp(normalizedEmail);
        log.info("Password reset OTP generated for userId={}", userOptional.get().getId());

        mailSender.ifPresentOrElse(
                sender -> dispatchOtpEmail(sender, normalizedEmail, otp),
                () -> log.warn("JavaMailSender not configured; OTP email skipped for userId={}", userOptional.get().getId())
        );
    }

    /**
     * Verifies an OTP and returns a short-lived password reset token.
     *
     * @param email account email
     * @param otp   one-time passcode
     * @return reset token
     */
    public String verifyOtp(String email, String otp) {
        String normalizedEmail = StringUtils.trimToEmpty(email).toLowerCase();
        boolean valid = otpStore.verifyOtp(normalizedEmail, otp);
        if (!valid) {
            throw new IllegalArgumentException("Invalid or expired OTP");
        }
        return otpStore.createResetToken(normalizedEmail);
    }

    /**
     * Updates account password using a verified reset token.
     *
     * @param resetToken  one-time reset token
     * @param newPassword raw new password to encode
     */
    public void resetPasswordWithToken(String resetToken, String newPassword) {
        String email = otpStore.getEmailByResetToken(resetToken);
        if (StringUtils.isBlank(email)) {
            throw new IllegalArgumentException("Invalid or expired reset session");
        }

        User userEntity = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        userEntity.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(userEntity);
        otpStore.removeResetToken(resetToken);
        log.info("Password reset completed for userId={}", userEntity.getId());
    }

    private void dispatchOtpEmail(JavaMailSender sender, String email, String otp) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(email);
            message.setSubject("Upcraft PG - Password Reset OTP");
            message.setText("Hello,\n\nYour OTP for password reset is: " + otp
                    + "\n\nThis OTP is valid for 10 minutes. "
                    + "If you did not request this, please ignore this email.\n\nRegards,\nUpcraft PG Team");
            sender.send(message);
            log.info("Password reset OTP email dispatched.");
        } catch (Exception exception) {
            log.error("Failed to send password reset OTP email.", exception);
        }
    }
}
