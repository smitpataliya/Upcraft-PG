package com.upcraft.stay.operations.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request payload for assigning a complaint to a staff user or manager.
 */
public record AssignComplaintRequestDTO(
        @NotNull(message = "Assignee user ID is required")
        UUID assignedToUserId,
        @Size(max = 500, message = "Assignment note cannot exceed 500 characters")
        String note
) {
}

