package com.upcraft.stay.operations.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.upcraft.stay.operations.enums.DocumentCategory;
import com.upcraft.stay.operations.enums.DocumentScopeType;

/**
 * Request payload linking an uploaded private stored file to a PG business scope.
 */
public record CreateDocumentRequestDTO(
        UUID propertyId,
        @NotNull(message = "Stored file ID is required")
        UUID storedFileId,
        @NotNull(message = "Scope type is required")
        DocumentScopeType scopeType,
        @NotNull(message = "Document category is required")
        DocumentCategory documentCategory,
        @NotBlank(message = "Document title is required")
        @Size(max = 180, message = "Title cannot exceed 180 characters")
        String title,
        @Size(max = 2000, message = "Description cannot exceed 2000 characters")
        String description,
        UUID tenantId,
        UUID tenancyId,
        UUID invoiceId,
        UUID paymentId,
        boolean sharedWithTenant
) {
}

