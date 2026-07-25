package com.upcraft.stay.dto;

import com.upcraft.stay.domain.enums.MealType;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class CreateMealRequest {
    @NotBlank
    private String name;

    private String description;

    private String imageUrl;

    @NotNull
    private MealType type;

    private String servingTime;

    private boolean veg = true;

    @NotNull
    private LocalDate dateScheduled;
}
