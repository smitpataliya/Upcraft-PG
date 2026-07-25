package com.upcraft.stay.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class RoommateDto {
    private UUID id;
    private String name;
    private String bedNumber;
}
