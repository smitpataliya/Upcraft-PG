package com.upcraft.stay.controller;

import com.upcraft.stay.domain.entity.User;
import com.upcraft.stay.domain.enums.Role;
import com.upcraft.stay.dto.AuthResponse;
import com.upcraft.stay.dto.CreateOwnerRequest;
import com.upcraft.stay.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @GetMapping("/owners")
    public ResponseEntity<List<AuthResponse>> getOwners() {
        return ResponseEntity.ok(userRepository.findAllByRole(Role.PG_OWNER).stream()
                .map(user -> AuthResponse.builder()
                        .id(user.getId())
                        .name(user.getName())
                        .email(user.getEmail())
                        .role(user.getRole())
                        .phone(user.getPhone())
                        .avatarUrl(user.getAvatarUrl())
                        .build())
                .collect(Collectors.toList()));
    }

    @PostMapping("/owners")
    public ResponseEntity<AuthResponse> createOwner(@Valid @RequestBody CreateOwnerRequest req) {
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already in use");
        }

        User owner = User.builder()
                .name(req.getName())
                .email(req.getEmail())
                .password(passwordEncoder.encode(req.getPassword()))
                .phone(req.getPhone())
                .role(Role.PG_OWNER)
                .active(true)
                .build();

        userRepository.save(owner);

        return ResponseEntity.status(HttpStatus.CREATED).body(AuthResponse.builder()
                .id(owner.getId())
                .name(owner.getName())
                .email(owner.getEmail())
                .role(owner.getRole())
                .phone(owner.getPhone())
                .avatarUrl(owner.getAvatarUrl())
                .build());
    }

    @DeleteMapping("/owners/{id}")
    public ResponseEntity<Void> deactivateOwner(@PathVariable UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Owner not found"));
        if (user.getRole() != Role.PG_OWNER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Target must be an owner");
        }
        user.setActive(false);
        userRepository.save(user);
        return ResponseEntity.noContent().build();
    }
}
