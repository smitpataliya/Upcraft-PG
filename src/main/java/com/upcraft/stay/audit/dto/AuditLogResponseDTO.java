package com.upcraft.stay.audit.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.upcraft.stay.audit.enums.AuditAction;
import com.upcraft.stay.audit.enums.AuditEntityType;

/**
 * Read-only audit trail item exposed to authorized PG Owners and Managers.
 */
public record AuditLogResponseDTO(
        UUID id,
        UUID organizationId,
        UUID propertyId,
        String propertyName,
        UUID actorUserId,
        String actorEmail,
        String actorRole,
        AuditAction action,
        AuditEntityType entityType,
        UUID entityId,
        String summary,
        Map<String, String> metadata,
        String clientIp,
        String traceId,
        Instant createdAt
) {
}

