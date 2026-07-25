package com.upcraft.stay.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class RoomSummaryDto {
    private UUID id;
    private String number;
    private Integer floor;
    private String sharing;
}
