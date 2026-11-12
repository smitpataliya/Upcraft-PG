package com.upcraft.stay.operations.dto;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.upcraft.stay.operations.enums.AnnouncementAudienceType;

/**
 * Request payload for creating or updating an announcement.
 */
public record UpsertAnnouncementV1RequestDTO(
        UUID propertyId,
        @NotBlank(message = "Announcement title is required")
        @Size(max = 255, message = "Title cannot exceed 255 characters")
        String title,
        @NotBlank(message = "Announcement body is required")
        @Size(max = 5000, message = "Body cannot exceed 5000 characters")
        String body,
        @Size(max = 80, message = "Category cannot exceed 80 characters")
        String category,
        @NotNull(message = "Audience type is required")
        AnnouncementAudienceType audienceType,
        boolean pinned,
        Instant publishAt,
        Instant expiresAt,
        UUID attachmentFileId
) {
}

