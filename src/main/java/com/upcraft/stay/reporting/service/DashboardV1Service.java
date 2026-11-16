package com.upcraft.stay.reporting.service;

import java.util.UUID;

import com.upcraft.stay.reporting.dto.StaffDashboardResponseDTO;
import com.upcraft.stay.reporting.dto.TenantDashboardResponseDTO;

/**
 * Real database-backed dashboard analytics service for Owner, Manager, and Tenant roles.
 */
public interface DashboardV1Service {

    StaffDashboardResponseDTO getStaffDashboard(
            String principalEmail,
            UUID propertyId,
            Integer billingYear,
            Integer billingMonth
    );

    TenantDashboardResponseDTO getTenantDashboard(String principalEmail);
}

