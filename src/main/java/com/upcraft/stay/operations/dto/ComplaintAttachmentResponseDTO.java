package com.upcraft.stay.operations.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Private attachment metadata linked to a complaint or comment.
 */
public record ComplaintAttachmentResponseDTO(
        UUID id,
        UUID storedFileId,
        UUID commentId,
        String originalFilename,
        String contentType,
        Long fileSizeBytes,
        String downloadUrl,
        Instant createdAt
) {
}

