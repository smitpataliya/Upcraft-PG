package com.upcraft.stay.controller;

import com.upcraft.stay.dto.*;
import com.upcraft.stay.service.MemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    /** OWNER: list all members */
    @GetMapping
    public ResponseEntity<List<MemberDto>> getAll() {
        return ResponseEntity.ok(memberService.getAllMembers());
    }

    /** OWNER: add a new member */
    @PostMapping
    public ResponseEntity<MemberDto> create(@Valid @RequestBody CreateMemberRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(memberService.createMember(req));
    }

    /** OWNER: deactivate a member */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        memberService.deactivateMember(id);
        return ResponseEntity.noContent().build();
    }

    /** OWNER: get a member by ID */
    @GetMapping("/{id}")
    public ResponseEntity<MemberDto> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(memberService.getMemberById(id));
    }

    /** OWNER: assign a room to a member */
    @PostMapping("/{id}/assign-room")
    public ResponseEntity<Void> assignRoom(@PathVariable UUID id, @Valid @RequestBody AssignRoomRequest req) {
        memberService.assignRoom(id, req);
        return ResponseEntity.noContent().build();
    }

    /** OWNER: reset member password */
    @PatchMapping("/{id}/password")
    public ResponseEntity<Void> resetMemberPassword(@PathVariable UUID id, @Valid @RequestBody AdminResetPasswordRequest req) {
        memberService.resetMemberPassword(id, req.getNewPassword());
        return ResponseEntity.noContent().build();
    }

    /** ANY AUTH: update own profile */
    @PutMapping("/profile")
    public ResponseEntity<MemberDto> updateProfile(Principal principal, @RequestBody UpdateProfileRequest req) {
        return ResponseEntity.ok(memberService.updateProfile(principal.getName(), req));
    }

    /** ANY AUTH: change own password */
    @PutMapping("/profile/password")
    public ResponseEntity<Void> changePassword(Principal principal, @Valid @RequestBody ChangePasswordRequest req) {
        memberService.changePassword(principal.getName(), req);
        return ResponseEntity.noContent().build();
    }
}
