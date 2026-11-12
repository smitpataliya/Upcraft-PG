package com.upcraft.stay.operations.service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.upcraft.stay.operations.dto.MealV1ResponseDTO;
import com.upcraft.stay.operations.dto.UpsertMealV1RequestDTO;

/**
 * Property-scoped weekly and daily PG food menu service (read-only for residents).
 */
public interface FoodMenuV1Service {

    List<MealV1ResponseDTO> getWeeklyMenu(String principalEmail, UUID propertyId, LocalDate startDate);

    MealV1ResponseDTO createMeal(String principalEmail, UpsertMealV1RequestDTO request);

    MealV1ResponseDTO updateMeal(String principalEmail, UUID mealId, UpsertMealV1RequestDTO request);

    void deleteMeal(String principalEmail, UUID mealId);
}

