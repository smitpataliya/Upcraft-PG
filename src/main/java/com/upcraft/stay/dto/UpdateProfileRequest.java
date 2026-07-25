package com.upcraft.stay.dto;

import lombok.Data;

@Data
public class UpdateProfileRequest {
    private String name;
    private String phone;
    private String emergencyContact;
    private String address;
}
