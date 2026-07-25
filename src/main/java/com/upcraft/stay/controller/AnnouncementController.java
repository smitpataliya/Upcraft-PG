package com.upcraft.stay.controller;

import com.upcraft.stay.dto.AnnouncementDto;
import com.upcraft.stay.dto.CreateAnnouncementRequest;
import com.upcraft.stay.service.AnnouncementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/announcements")
@RequiredArgsConstructor
public class AnnouncementController {

    private final AnnouncementService announcementService;

    /** ANY AUTH: view all announcements, pinned first */
    @GetMapping
    public ResponseEntity<List<AnnouncementDto>> getAll() {
        return ResponseEntity.ok(announcementService.getAll());
    }

    /** OWNER: publish an announcement */
    @PostMapping
    public ResponseEntity<AnnouncementDto> create(Principal principal, @Valid @RequestBody CreateAnnouncementRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(announcementService.create(principal.getName(), req));
    }

    /** OWNER: update an announcement */
    @PutMapping("/{id}")
    public ResponseEntity<AnnouncementDto> update(@PathVariable UUID id, @Valid @RequestBody CreateAnnouncementRequest req) {
        return ResponseEntity.ok(announcementService.update(id, req));
    }

    /** OWNER: delete an announcement */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        announcementService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
