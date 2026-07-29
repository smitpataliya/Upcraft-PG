package com.upcraft.stay.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class UpdateMemberRequest {
    private String name;
    private String email;
    private String username;
    private String phone;
    private String emergencyContact;
    private String address;
    private String bedNumber;
    private LocalDate checkInDate;
    private UUID roomId;
    private Boolean active;
}
