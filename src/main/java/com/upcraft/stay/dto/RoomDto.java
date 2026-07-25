package com.upcraft.stay.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class RoomDto {
    private UUID id;
    private String number;
    private Integer floor;
    private String sharing;
    private Integer capacity;
    private Integer occupied;
    private Double price;
    private List<String> amenities;
    private LocalDateTime createdAt;
}
