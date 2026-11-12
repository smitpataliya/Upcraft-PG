package com.upcraft.stay.organization.dto;

import java.util.UUID;

/**
 * Manager property assignment and permission projection.
 */
public record ManagerAssignmentResponseDTO(
        UUID id,
        UUID organizationId,
        UUID propertyId,
        String propertyName,
        UUID managerUserId,
        String managerName,
        String managerEmail,
        boolean canManageTenants,
        boolean canRecordPayments,
        boolean canEnterMeters,
        boolean canHandleComplaints,
        boolean canPublishNotices,
        boolean active
) {}

