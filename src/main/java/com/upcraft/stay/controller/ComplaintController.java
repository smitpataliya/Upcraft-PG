package com.upcraft.stay.controller;

import com.upcraft.stay.dto.*;
import com.upcraft.stay.service.ComplaintService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/complaints")
@RequiredArgsConstructor
public class ComplaintController {

    private final ComplaintService complaintService;

    /** MEMBER: raise a new complaint */
    @PostMapping
    @PreAuthorize("hasRole('PG_MEMBER')")
    public ResponseEntity<ComplaintDto> raise(Principal principal, @Valid @RequestBody CreateComplaintRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(complaintService.raise(principal.getName(), req));
    }

    /** MEMBER: view own complaints */
    @GetMapping("/mine")
    @PreAuthorize("hasRole('PG_MEMBER')")
    public ResponseEntity<List<ComplaintDto>> mine(Principal principal) {
        return ResponseEntity.ok(complaintService.getMyComplaints(principal.getName()));
    }

    /** OWNER: view all complaints */
    @GetMapping
    @PreAuthorize("hasRole('PG_OWNER')")
    public ResponseEntity<List<ComplaintDto>> all() {
        return ResponseEntity.ok(complaintService.getAllComplaints());
    }

    /** OWNER: update status and add timeline entry */
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('PG_OWNER')")
    public ResponseEntity<ComplaintDto> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateComplaintStatusRequest req,
            Principal principal) {
        return ResponseEntity.ok(complaintService.updateStatus(id, req, principal.getName()));
    }
}
