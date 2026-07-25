package com.upcraft.stay.controller;

import com.upcraft.stay.dto.MealDto;
import com.upcraft.stay.dto.RoomDto;
import com.upcraft.stay.service.MenuService;
import com.upcraft.stay.service.RoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Public endpoints — no authentication required.
 * Used by the public-facing homepage to show live rooms and today's menu
 * without requiring visitors to log in.
 */
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicController {

    private final RoomService roomService;
    private final MenuService menuService;

    /** Public: list all rooms (for the homepage Rooms section) */
    @GetMapping("/rooms")
    public ResponseEntity<List<RoomDto>> rooms() {
        return ResponseEntity.ok(roomService.getAllRooms());
    }

    /** Public: today's menu (for the homepage Food section) */
    @GetMapping("/menu/today")
    public ResponseEntity<List<MealDto>> todayMenu() {
        return ResponseEntity.ok(menuService.getTodayMeals());
    }
}
