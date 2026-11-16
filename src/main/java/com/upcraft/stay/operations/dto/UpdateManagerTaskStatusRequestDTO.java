package com.upcraft.stay.operations.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.upcraft.stay.operations.enums.ManagerTaskStatus;

/**
 * Request payload for updating a manager task status.
 */
public record UpdateManagerTaskStatusRequestDTO(
                @NotNull(message = "Task status is required") ManagerTaskStatus taskStatus,
                @Size(max = 1000, message = "Completion notes cannot exceed 1000 characters") String completionNotes) {
}
