package com.upcraft.stay.audit.controller;

import java.security.Principal;
import java.time.LocalDate;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.upcraft.stay.audit.dto.AuditLogResponseDTO;
import com.upcraft.stay.audit.enums.AuditAction;
import com.upcraft.stay.audit.enums.AuditEntityType;
import com.upcraft.stay.audit.service.AuditLogService;
import com.upcraft.stay.common.constants.AppConstants;
import com.upcraft.stay.common.dto.ApiResponseDTO;
import com.upcraft.stay.common.dto.PageResponseDTO;

/**
 * Read-only REST controller exposing immutable audit logs to authorized PG Owners and Managers.
 */
@RestController
@RequestMapping("/api/v1/audit-logs")
@RequiredArgsConstructor
public class AuditLogV1Controller {

    private final AuditLogService auditLogService;

    @GetMapping
    @PreAuthorize("hasAnyRole('PG_OWNER', 'MANAGER')")
    public ResponseEntity<ApiResponseDTO<PageResponseDTO<AuditLogResponseDTO>>> listAuditLogs(
            Principal principal,
            @RequestParam(required = false) UUID propertyId,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) AuditEntityType entityType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") Integer size
    ) {
        PageResponseDTO<AuditLogResponseDTO> auditLogs = auditLogService.listAuditLogs(
                principal.getName(), propertyId, action, entityType, fromDate, toDate, page, size
        );
        return ResponseEntity.ok(ApiResponseDTO.success("Audit logs loaded", auditLogs, traceId()));
    }

    private String traceId() {
        return MDC.get(AppConstants.TRACE_ID_MDC_KEY);
    }
}

