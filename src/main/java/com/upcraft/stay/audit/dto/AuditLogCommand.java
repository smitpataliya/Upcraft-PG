package com.upcraft.stay.audit.dto;

import java.util.Map;
import java.util.UUID;

import com.upcraft.stay.audit.enums.AuditAction;
import com.upcraft.stay.audit.enums.AuditEntityType;
import com.upcraft.stay.domain.entity.User;
import com.upcraft.stay.organization.entity.OrganizationEntity;
import com.upcraft.stay.property.entity.PropertyEntity;

/**
 * Immutable internal command describing a sanitized audit event.
 */
public record AuditLogCommand(
        OrganizationEntity organization,
        PropertyEntity property,
        User actorUser,
        String actorEmailFallback,
        AuditAction action,
        AuditEntityType entityType,
        UUID entityId,
        String summary,
        Map<String, Object> metadata
) {
}

