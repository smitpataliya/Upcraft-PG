package com.upcraft.stay.dto;

import com.upcraft.stay.domain.enums.Role;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class AuthResponse {
    private String token;
    private String type;
    private UUID id;
    private String name;
    private String email;
    private Role role;
    private String phone;
    private String avatarUrl;
}
