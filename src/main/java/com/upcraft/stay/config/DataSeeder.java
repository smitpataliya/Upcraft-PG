package com.upcraft.stay.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.upcraft.stay.domain.entity.User;
import com.upcraft.stay.domain.enums.Role;
import com.upcraft.stay.repository.UserRepository;

/**
 * Optional development/local seed runner gated strictly to {@code dev} and {@code local} profiles.
 * Reads initial passwords strictly from configuration and never executes DDL or logs credentials.
 */
@Slf4j
@Component
@Profile({"dev", "local"})
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.dev-seed.owner-email:owner@pg.com}")
    private String ownerEmail;

    @Value("${app.dev-seed.owner-password:}")
    private String ownerPassword;

    @Value("${app.dev-seed.admin-email:admin@pg.com}")
    private String adminEmail;

    @Value("${app.dev-seed.admin-password:}")
    private String adminPassword;

    @Override
    public void run(String... args) {
        seedAccountIfConfigured(ownerEmail, ownerPassword, "PG Owner", Role.PG_OWNER);
        seedAccountIfConfigured(adminEmail, adminPassword, "Super Admin", Role.SUPER_ADMIN);
    }

    private void seedAccountIfConfigured(String email, String rawPassword, String displayName, Role role) {
        if (StringUtils.isBlank(rawPassword) || StringUtils.isBlank(email)) {
            return;
        }
        String normalizedEmail = email.trim().toLowerCase();
        if (userRepository.existsByEmail(normalizedEmail)) {
            return;
        }
        User userEntity = User.builder()
                .name(displayName)
                .email(normalizedEmail)
                .password(passwordEncoder.encode(rawPassword))
                .role(role)
                .active(true)
                .build();
        userRepository.save(userEntity);
        log.info("Development account seeded for role={}", role);
    }
}
