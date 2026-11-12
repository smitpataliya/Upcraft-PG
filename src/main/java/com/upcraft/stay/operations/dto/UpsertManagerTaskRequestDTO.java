package com.upcraft.stay.operations.dto;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.upcraft.stay.operations.enums.ManagerTaskPriority;

/**
 * Request payload for creating or updating a manager operational task.
 */
public record UpsertManagerTaskRequestDTO(
        @NotNull(message = "Property ID is required")
        UUID propertyId,
        UUID assignedToUserId,
        UUID complaintId,
        @NotBlank(message = "Task title is required")
        @Size(max = 180, message = "Title cannot exceed 180 characters")
        String title,
        @Size(max = 2000, message = "Description cannot exceed 2000 characters")
        String description,
        @NotNull(message = "Priority is required")
        ManagerTaskPriority priority,
        Instant dueAt
) {
}

