package com.upcraft.stay.operations.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.upcraft.stay.domain.enums.ComplaintStatus;

/**
 * Request payload for advancing or reopening a complaint status.
 */
public record UpdateComplaintStatusV1RequestDTO(
        @NotNull(message = "Complaint status is required")
        ComplaintStatus status,
        @Size(max = 1000, message = "Status note cannot exceed 1000 characters")
        String note,
        @Size(max = 2000, message = "Resolution summary cannot exceed 2000 characters")
        String resolutionSummary
) {
}

