package com.upcraft.stay.operations.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Response confirming announcement read acknowledgment.
 */
public record AnnouncementReadResponseDTO(
        UUID announcementId,
        Instant readAt,
        boolean alreadyRead
) {
}

