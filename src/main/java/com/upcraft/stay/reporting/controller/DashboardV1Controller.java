package com.upcraft.stay.reporting.controller;

import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.upcraft.stay.common.constants.AppConstants;
import com.upcraft.stay.common.dto.ApiResponseDTO;
import com.upcraft.stay.reporting.dto.StaffDashboardResponseDTO;
import com.upcraft.stay.reporting.dto.TenantDashboardResponseDTO;
import com.upcraft.stay.reporting.service.DashboardV1Service;

/**
 * Real database-backed dashboard analytics endpoints for Owner, Manager, and Tenant roles.
 */
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardV1Controller {

    private final DashboardV1Service dashboardV1Service;

    @GetMapping("/owner")
    @PreAuthorize("hasRole('PG_OWNER')")
    public ResponseEntity<ApiResponseDTO<StaffDashboardResponseDTO>> ownerDashboard(
            Authentication authentication,
            @RequestParam(required = false) UUID propertyId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month
    ) {
        return ResponseEntity.ok(ApiResponseDTO.success(
                "Owner dashboard loaded",
                dashboardV1Service.getStaffDashboard(authentication.getName(), propertyId, year, month),
                MDC.get(AppConstants.TRACE_ID_MDC_KEY)
        ));
    }

    @GetMapping("/manager")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ApiResponseDTO<StaffDashboardResponseDTO>> managerDashboard(
            Authentication authentication,
            @RequestParam(required = false) UUID propertyId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month
    ) {
        return ResponseEntity.ok(ApiResponseDTO.success(
                "Manager dashboard loaded",
                dashboardV1Service.getStaffDashboard(authentication.getName(), propertyId, year, month),
                MDC.get(AppConstants.TRACE_ID_MDC_KEY)
        ));
    }

    @GetMapping("/tenant")
    @PreAuthorize("hasRole('PG_MEMBER')")
    public ResponseEntity<ApiResponseDTO<TenantDashboardResponseDTO>> tenantDashboard(Authentication authentication) {
        return ResponseEntity.ok(ApiResponseDTO.success(
                "Tenant dashboard loaded",
                dashboardV1Service.getTenantDashboard(authentication.getName()),
                MDC.get(AppConstants.TRACE_ID_MDC_KEY)
        ));
    }
}

