package com.upcraft.stay.service;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class OtpStore {

    public static class OtpEntry {
        private final String code;
        private final LocalDateTime expiresAt;

        public OtpEntry(String code, LocalDateTime expiresAt) {
            this.code = code;
            this.expiresAt = expiresAt;
        }

        public String getCode() { return code; }
        public LocalDateTime getExpiresAt() { return expiresAt; }
    }

    private final Map<String, OtpEntry> otpMap = new ConcurrentHashMap<>();
    private final Map<String, String> resetTokenMap = new ConcurrentHashMap<>();

    private final Random random = new Random();

    public String generateOtp(String email) {
        String code = String.format("%06d", random.nextInt(1000000));
        otpMap.put(email.toLowerCase(), new OtpEntry(code, LocalDateTime.now().plusMinutes(10)));
        return code;
    }

    public boolean verifyOtp(String email, String code) {
        String key = email.toLowerCase();
        OtpEntry entry = otpMap.get(key);
        if (entry == null) return false;
        if (LocalDateTime.now().isAfter(entry.getExpiresAt())) {
            otpMap.remove(key);
            return false;
        }
        if (entry.getCode().equals(code.trim())) {
            otpMap.remove(key);
            return true;
        }
        return false;
    }

    public String createResetToken(String email) {
        String token = UUID.randomUUID().toString();
        resetTokenMap.put(token, email.toLowerCase());
        return token;
    }

    public String getEmailByResetToken(String token) {
        return resetTokenMap.get(token);
    }

    public void removeResetToken(String token) {
        resetTokenMap.remove(token);
    }
}
