package com.upcraft.stay.controller;

import com.upcraft.stay.dto.CreateMealRequest;
import com.upcraft.stay.dto.MealDto;
import com.upcraft.stay.service.MenuService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/menu")
@RequiredArgsConstructor
public class MenuController {

    private final MenuService menuService;

    /** ANY AUTH: today's meals */
    @GetMapping("/today")
    public ResponseEntity<List<MealDto>> today() {
        return ResponseEntity.ok(menuService.getTodayMeals());
    }

    /** ANY AUTH: upcoming 7 days */
    @GetMapping("/weekly")
    public ResponseEntity<List<MealDto>> weekly() {
        return ResponseEntity.ok(menuService.getWeeklyMeals());
    }

    /** OWNER: add a meal */
    @PostMapping
    public ResponseEntity<MealDto> add(@Valid @RequestBody CreateMealRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(menuService.addMeal(req));
    }

    /** OWNER: update a meal */
    @PutMapping("/{id}")
    public ResponseEntity<MealDto> update(@PathVariable UUID id, @Valid @RequestBody CreateMealRequest req) {
        return ResponseEntity.ok(menuService.updateMeal(id, req));
    }

    /** OWNER: delete a meal */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        menuService.deleteMeal(id);
        return ResponseEntity.noContent().build();
    }
}
