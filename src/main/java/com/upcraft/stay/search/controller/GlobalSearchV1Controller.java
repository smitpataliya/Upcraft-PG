package com.upcraft.stay.search.controller;

import java.security.Principal;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.upcraft.stay.common.constants.AppConstants;
import com.upcraft.stay.common.dto.ApiResponseDTO;
import com.upcraft.stay.search.dto.GlobalSearchResponseDTO;
import com.upcraft.stay.search.service.GlobalSearchService;

/**
 * Versioned REST controller serving role- and property-scoped global search across core PG entities.
 */
@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class GlobalSearchV1Controller {

    private final GlobalSearchService globalSearchService;

    @GetMapping
    @PreAuthorize("hasAnyRole('PG_OWNER', 'MANAGER', 'PG_MEMBER')")
    public ResponseEntity<ApiResponseDTO<GlobalSearchResponseDTO>> search(
            Principal principal,
            @RequestParam("q") String query,
            @RequestParam(required = false) UUID propertyId
    ) {
        GlobalSearchResponseDTO results = globalSearchService.search(principal.getName(), query, propertyId);
        return ResponseEntity.ok(ApiResponseDTO.success("Search completed", results, traceId()));
    }

    private String traceId() {
        return MDC.get(AppConstants.TRACE_ID_MDC_KEY);
    }
}

