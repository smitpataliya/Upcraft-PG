package com.upcraft.stay.operations.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.upcraft.stay.domain.enums.ComplaintCategory;
import com.upcraft.stay.domain.enums.ComplaintPriority;

/**
 * Request payload for creating a multi-property tenant complaint.
 */
public record CreateComplaintV1RequestDTO(
        UUID propertyId,
        @NotBlank(message = "Complaint title is required")
        @Size(max = 255, message = "Complaint title cannot exceed 255 characters")
        String title,
        @NotBlank(message = "Complaint description is required")
        @Size(max = 4000, message = "Complaint description cannot exceed 4000 characters")
        String description,
        @NotNull(message = "Complaint category is required")
        ComplaintCategory category,
        @NotNull(message = "Complaint priority is required")
        ComplaintPriority priority,
        @Size(max = 80, message = "Preferred contact time cannot exceed 80 characters")
        String preferredContactTime,
        List<UUID> attachmentFileIds
) {
}

