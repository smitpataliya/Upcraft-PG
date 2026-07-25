package com.upcraft.stay.controller;

import com.upcraft.stay.dto.*;
import com.upcraft.stay.service.RoomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;

    /** OWNER: list all rooms */
    @GetMapping
    public ResponseEntity<List<RoomDto>> getAll() {
        return ResponseEntity.ok(roomService.getAllRooms());
    }

    /** OWNER: create a room */
    @PostMapping
    public ResponseEntity<RoomDto> create(@Valid @RequestBody CreateRoomRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(roomService.createRoom(req));
    }

    /** OWNER: update a room */
    @PutMapping("/{id}")
    public ResponseEntity<RoomDto> update(@PathVariable UUID id, @Valid @RequestBody CreateRoomRequest req) {
        return ResponseEntity.ok(roomService.updateRoom(id, req));
    }

    /** OWNER: delete a room */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        roomService.deleteRoom(id);
        return ResponseEntity.noContent().build();
    }

    /** MEMBER: view own room + roommates */
    @GetMapping("/mine")
    public ResponseEntity<MyRoomDto> getMyRoom(Principal principal) {
        return ResponseEntity.ok(roomService.getMyRoom(principal.getName()));
    }
}
