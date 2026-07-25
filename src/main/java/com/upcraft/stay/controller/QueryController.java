package com.upcraft.stay.controller;

import com.upcraft.stay.dto.*;
import com.upcraft.stay.service.QueryService;
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
@RequestMapping("/api/queries")
@RequiredArgsConstructor
public class QueryController {

    private final QueryService queryService;

    /** MEMBER: create a query */
    @PostMapping
    @PreAuthorize("hasRole('PG_MEMBER')")
    public ResponseEntity<QueryDto> create(Principal principal, @Valid @RequestBody CreateQueryRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(queryService.create(principal.getName(), req));
    }

    /** MEMBER: view own queries */
    @GetMapping("/mine")
    @PreAuthorize("hasRole('PG_MEMBER')")
    public ResponseEntity<List<QueryDto>> mine(Principal principal) {
        return ResponseEntity.ok(queryService.getMyQueries(principal.getName()));
    }

    /** OWNER: view all queries */
    @GetMapping
    @PreAuthorize("hasRole('PG_OWNER')")
    public ResponseEntity<List<QueryDto>> all() {
        return ResponseEntity.ok(queryService.getAllQueries());
    }

    /** OWNER or MEMBER: add a reply to a query */
    @PostMapping("/{id}/reply")
    public ResponseEntity<QueryDto> reply(
            @PathVariable UUID id,
            @Valid @RequestBody ReplyRequest req,
            Principal principal) {
        return ResponseEntity.ok(queryService.addReply(id, req, principal.getName()));
    }

    /** OWNER: close a query */
    @PatchMapping("/{id}/close")
    @PreAuthorize("hasRole('PG_OWNER')")
    public ResponseEntity<QueryDto> close(@PathVariable UUID id) {
        return ResponseEntity.ok(queryService.closeQuery(id));
    }
}
