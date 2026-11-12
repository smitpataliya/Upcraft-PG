package com.upcraft.stay.operations.service;

import java.util.UUID;

import com.upcraft.stay.common.dto.PageResponseDTO;
import com.upcraft.stay.operations.dto.CreateDocumentRequestDTO;
import com.upcraft.stay.operations.dto.DocumentResponseDTO;
import com.upcraft.stay.operations.enums.DocumentCategory;
import com.upcraft.stay.operations.enums.DocumentScopeType;

/**
 * Private multi-scope PG document registry backed by {@code stored_files}.
 */
public interface DocumentV1Service {

    PageResponseDTO<DocumentResponseDTO> listDocuments(
            String principalEmail,
            UUID propertyId,
            DocumentScopeType scopeType,
            DocumentCategory documentCategory,
            UUID tenantId,
            UUID tenancyId,
            int page,
            Integer size
    );

    DocumentResponseDTO createDocument(String principalEmail, CreateDocumentRequestDTO request);

    void deleteDocument(String principalEmail, UUID documentId);
}

