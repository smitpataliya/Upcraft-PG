package com.upcraft.stay.dto;

import com.upcraft.stay.domain.enums.MealType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Data
@Builder
public class MealDto {
    private UUID id;
    private String name;
    private String description;
    private String imageUrl;
    private MealType type;
    private String servingTime;
    private boolean veg;
    private boolean available;
    private LocalDate dateScheduled;
}
