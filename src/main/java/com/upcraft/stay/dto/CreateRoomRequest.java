package com.upcraft.stay.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.List;

@Data
public class CreateRoomRequest {
    @NotBlank
    private String number;

    @NotNull
    private Integer floor;

    @NotBlank
    private String sharing;

    @NotNull
    @Min(1)
    private Integer capacity;

    @NotNull
    @Positive
    private Double price;

    private List<String> amenities;
}
