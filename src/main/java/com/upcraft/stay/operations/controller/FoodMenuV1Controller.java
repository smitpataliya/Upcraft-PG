package com.upcraft.stay.operations.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.upcraft.stay.common.constants.AppConstants;
import com.upcraft.stay.common.dto.ApiResponseDTO;
import com.upcraft.stay.operations.dto.MealV1ResponseDTO;
import com.upcraft.stay.operations.dto.UpsertMealV1RequestDTO;
import com.upcraft.stay.operations.service.FoodMenuV1Service;

/**
 * Property-scoped weekly PG food menu controller (read-only for residents).
 */
@RestController
@RequestMapping("/api/v1/food-menu")
@RequiredArgsConstructor
public class FoodMenuV1Controller {

    private final FoodMenuV1Service foodMenuV1Service;

    @GetMapping("/weekly")
    public ResponseEntity<ApiResponseDTO<List<MealV1ResponseDTO>>> weeklyMenu(
            Authentication authentication,
            @RequestParam(required = false) UUID propertyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate
    ) {
        return ResponseEntity.ok(ApiResponseDTO.success(
                "Weekly food menu loaded",
                foodMenuV1Service.getWeeklyMenu(authentication.getName(), propertyId, startDate),
                MDC.get(AppConstants.TRACE_ID_MDC_KEY)
        ));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('PG_OWNER','MANAGER')")
    public ResponseEntity<ApiResponseDTO<MealV1ResponseDTO>> createMeal(
            Authentication authentication,
            @Valid @RequestBody UpsertMealV1RequestDTO request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponseDTO.success(
                "Meal added to menu",
                foodMenuV1Service.createMeal(authentication.getName(), request),
                MDC.get(AppConstants.TRACE_ID_MDC_KEY)
        ));
    }

    @PutMapping("/{mealId}")
    @PreAuthorize("hasAnyRole('PG_OWNER','MANAGER')")
    public ResponseEntity<ApiResponseDTO<MealV1ResponseDTO>> updateMeal(
            Authentication authentication,
            @PathVariable UUID mealId,
            @Valid @RequestBody UpsertMealV1RequestDTO request
    ) {
        return ResponseEntity.ok(ApiResponseDTO.success(
                "Meal updated",
                foodMenuV1Service.updateMeal(authentication.getName(), mealId, request),
                MDC.get(AppConstants.TRACE_ID_MDC_KEY)
        ));
    }

    @DeleteMapping("/{mealId}")
    @PreAuthorize("hasAnyRole('PG_OWNER','MANAGER')")
    public ResponseEntity<Void> deleteMeal(
            Authentication authentication,
            @PathVariable UUID mealId
    ) {
        foodMenuV1Service.deleteMeal(authentication.getName(), mealId);
        return ResponseEntity.noContent().build();
    }
}

