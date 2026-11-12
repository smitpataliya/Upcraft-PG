package com.upcraft.stay.operations.dto;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import com.upcraft.stay.domain.enums.ComplaintCategory;
import com.upcraft.stay.domain.enums.ComplaintPriority;
import com.upcraft.stay.domain.enums.ComplaintStatus;

/**
 * Multi-property complaint summary response DTO.
 */
public record ComplaintV1ResponseDTO(
        UUID id,
        UUID propertyId,
        String propertyName,
        UUID tenantId,
        String tenantName,
        String roomNumber,
        String bedCode,
        String title,
        String description,
        ComplaintCategory category,
        ComplaintPriority priority,
        ComplaintStatus status,
        String preferredContactTime,
        String resolutionSummary,
        UUID memberId,
        String memberName,
        UUID assignedToUserId,
        String assignedToName,
        Instant acknowledgedAt,
        Instant resolvedAt,
        Instant closedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

