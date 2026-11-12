package com.upcraft.stay.operations.dto;

import java.time.Instant;
import java.util.UUID;

import com.upcraft.stay.operations.enums.DocumentCategory;
import com.upcraft.stay.operations.enums.DocumentScopeType;

/**
 * Private document response DTO pointing exclusively to the authorized file download endpoint.
 */
public record DocumentResponseDTO(
        UUID id,
        UUID propertyId,
        String propertyName,
        UUID storedFileId,
        String originalFilename,
        String contentType,
        Long fileSizeBytes,
        String downloadUrl,
        DocumentScopeType scopeType,
        DocumentCategory documentCategory,
        String title,
        String description,
        UUID tenantId,
        String tenantName,
        UUID tenancyId,
        UUID invoiceId,
        UUID paymentId,
        boolean sharedWithTenant,
        String uploadedByName,
        Instant createdAt
) {
}

