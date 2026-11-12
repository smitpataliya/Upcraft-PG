package com.upcraft.stay.operations.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Complaint thread comment DTO.
 */
public record ComplaintCommentResponseDTO(
        UUID id,
        UUID authorUserId,
        String authorName,
        String authorRole,
        String message,
        boolean internal,
        List<ComplaintAttachmentResponseDTO> attachments,
        Instant createdAt
) {
}

