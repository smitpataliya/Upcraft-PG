package com.upcraft.stay.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class MemberDto {
    private UUID id;
    private String name;
    private String email;
    private String phone;
    private boolean active;
    private String emergencyContact;
    private String address;
    private String bedNumber;
    private LocalDate checkInDate;
    private String avatarUrl;
    private String roomNumber;
    private RoomSummaryDto room;
    private LocalDateTime createdAt;
}
