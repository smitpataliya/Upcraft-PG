package com.upcraft.stay.operations.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload for posting a public reply or staff-only internal note on a complaint.
 */
public record CreateComplaintCommentRequestDTO(
        @NotBlank(message = "Comment message is required")
        @Size(max = 2000, message = "Comment cannot exceed 2000 characters")
        String message,
        boolean internal,
        List<UUID> attachmentFileIds
) {
}

