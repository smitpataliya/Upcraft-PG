package com.upcraft.stay.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class CreateMemberRequest {
    @NotBlank
    private String name;

    @Email
    @NotBlank
    private String email;

    private String username;

    @NotBlank
    private String phone;

    @NotBlank
    private String password;

    private String emergencyContact;
    private String address;
    private LocalDate checkInDate;
    private UUID roomId;
    private String bedNumber;
}
