package com.upcraft.stay.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class AssignRoomRequest {
    @NotNull
    private UUID roomId;
    private String bedNumber;
}
