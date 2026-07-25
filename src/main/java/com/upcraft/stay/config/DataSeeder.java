package com.upcraft.stay.config;

import com.upcraft.stay.domain.entity.User;
import com.upcraft.stay.domain.enums.Role;
import com.upcraft.stay.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import org.springframework.jdbc.core.JdbcTemplate;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        try {
            jdbcTemplate.execute("ALTER TABLE users MODIFY COLUMN role VARCHAR(50)");
            log.info("✅ Successfully altered users.role column to VARCHAR(50)");
        } catch (Exception e) {
            log.warn("Could not alter users.role column: {}", e.getMessage());
        }
        if (!userRepository.existsByEmail("owner@pg.com")) {
            User owner = User.builder()
                    .name("PG Owner")
                    .email("owner@pg.com")
                    .password(passwordEncoder.encode("owner@123"))
                    .phone("9999999999")
                    .role(Role.PG_OWNER)
                    .active(true)
                    .build();
            userRepository.save(owner);
            log.info("✅ Default PG_OWNER created → email: owner@pg.com | password: owner@123");
        } else {
            log.info("✅ PG_OWNER already exists, skipping seed.");
        }

        if (!userRepository.existsByEmail("admin@pg.com")) {
            User admin = User.builder()
                    .name("Super Admin")
                    .email("admin@pg.com")
                    .password(passwordEncoder.encode("admin@123"))
                    .phone("8888888888")
                    .role(Role.SUPER_ADMIN)
                    .active(true)
                    .build();
            userRepository.save(admin);
            log.info("✅ Default SUPER_ADMIN created → email: admin@pg.com | password: admin@123");
        } else {
            log.info("✅ SUPER_ADMIN already exists, skipping seed.");
        }
    }
}
