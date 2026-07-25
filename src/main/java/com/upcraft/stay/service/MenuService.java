package com.upcraft.stay.service;

import com.upcraft.stay.domain.entity.Meal;
import com.upcraft.stay.dto.CreateMealRequest;
import com.upcraft.stay.dto.MealDto;
import com.upcraft.stay.repository.MealRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MenuService {

    private final MealRepository mealRepository;

    @Transactional
    public MealDto addMeal(CreateMealRequest req) {
        Meal meal = Meal.builder()
                .name(req.getName())
                .description(req.getDescription())
                .imageUrl(req.getImageUrl())
                .type(req.getType())
                .servingTime(req.getServingTime())
                .veg(req.isVeg())
                .available(true)
                .dateScheduled(req.getDateScheduled())
                .build();
        return toDto(mealRepository.save(meal));
    }

    @Transactional
    public MealDto updateMeal(UUID id, CreateMealRequest req) {
        Meal meal = mealRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Meal not found"));
        meal.setName(req.getName());
        meal.setDescription(req.getDescription());
        meal.setImageUrl(req.getImageUrl());
        meal.setType(req.getType());
        meal.setServingTime(req.getServingTime());
        meal.setVeg(req.isVeg());
        meal.setDateScheduled(req.getDateScheduled());
        return toDto(mealRepository.save(meal));
    }

    @Transactional(readOnly = true)
    public List<MealDto> getTodayMeals() {
        return mealRepository.findByDateScheduledAndAvailableTrue(LocalDate.now())
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<MealDto> getWeeklyMeals() {
        LocalDate today = LocalDate.now();
        return mealRepository.findByDateScheduledBetweenAndAvailableTrue(today, today.plusDays(6))
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional
    public void deleteMeal(UUID id) {
        mealRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Meal not found"));
        mealRepository.deleteById(id);
    }

    public MealDto toDto(Meal m) {
        return MealDto.builder()
                .id(m.getId())
                .name(m.getName())
                .description(m.getDescription())
                .imageUrl(m.getImageUrl())
                .type(m.getType())
                .servingTime(m.getServingTime())
                .veg(m.isVeg())
                .available(m.isAvailable())
                .dateScheduled(m.getDateScheduled())
                .build();
    }
}
