package com.upcraft.stay.operations.dto;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import com.upcraft.stay.operations.enums.AnnouncementAudienceType;

/**
 * Multi-property announcement response DTO including current-user read flag and read count.
 */
public record AnnouncementV1ResponseDTO(
        UUID id,
        UUID propertyId,
        String propertyName,
        String title,
        String body,
        String category,
        AnnouncementAudienceType audienceType,
        boolean pinned,
        Instant publishAt,
        Instant expiresAt,
        UUID attachmentFileId,
        String attachmentUrl,
        String author,
        boolean readByCurrentUser,
        long readCount,
        LocalDateTime createdAt
) {
}

