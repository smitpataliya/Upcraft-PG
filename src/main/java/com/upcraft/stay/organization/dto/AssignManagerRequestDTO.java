package com.upcraft.stay.organization.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/**
 * Request payload to assign or update manager permissions on a property.
 */
public record AssignManagerRequestDTO(
        @NotNull(message = "Property ID is required")
        UUID propertyId,

        @NotNull(message = "Manager user ID is required")
        UUID managerUserId,

        Boolean canManageTenants,
        Boolean canRecordPayments,
        Boolean canEnterMeters,
        Boolean canHandleComplaints,
        Boolean canPublishNotices
) {}

