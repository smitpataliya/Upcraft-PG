package com.upcraft.stay.repository;

import com.upcraft.stay.domain.entity.Meal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface MealRepository extends JpaRepository<Meal, UUID> {
    List<Meal> findByDateScheduledAndAvailableTrue(LocalDate date);
    List<Meal> findByDateScheduledBetweenAndAvailableTrue(LocalDate start, LocalDate end);
}
