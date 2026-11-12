package com.upcraft.stay.operations.dto;

import java.time.Instant;
import java.util.UUID;

import com.upcraft.stay.operations.enums.ManagerTaskPriority;
import com.upcraft.stay.operations.enums.ManagerTaskStatus;

/**
 * Manager operational task response DTO.
 */
public record ManagerTaskResponseDTO(
        UUID id,
        UUID propertyId,
        String propertyName,
        UUID assignedToUserId,
        String assignedToName,
        UUID createdByUserId,
        String createdByName,
        UUID complaintId,
        String complaintTitle,
        String title,
        String description,
        ManagerTaskPriority priority,
        ManagerTaskStatus taskStatus,
        Instant dueAt,
        Instant completedAt,
        String completionNotes,
        Instant createdAt
) {
}

