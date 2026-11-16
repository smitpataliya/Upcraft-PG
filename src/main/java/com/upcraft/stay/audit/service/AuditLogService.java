package com.upcraft.stay.audit.service;

import java.time.LocalDate;
import java.util.UUID;

import com.upcraft.stay.audit.dto.AuditLogCommand;
import com.upcraft.stay.audit.dto.AuditLogResponseDTO;
import com.upcraft.stay.audit.enums.AuditAction;
import com.upcraft.stay.audit.enums.AuditEntityType;
import com.upcraft.stay.common.dto.PageResponseDTO;

/**
 * Service contract for recording and querying immutable security and operational audit logs.
 */
public interface AuditLogService {

    void recordEvent(AuditLogCommand command);

    void recordIndependentEvent(AuditLogCommand command);

    PageResponseDTO<AuditLogResponseDTO> listAuditLogs(
            String principalEmail,
            UUID propertyId,
            AuditAction action,
            AuditEntityType entityType,
            LocalDate fromDate,
            LocalDate toDate,
            int page,
            Integer size
    );
}

