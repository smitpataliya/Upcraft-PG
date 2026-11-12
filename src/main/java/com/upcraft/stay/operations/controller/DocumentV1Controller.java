package com.upcraft.stay.operations.controller;

import java.util.UUID;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.upcraft.stay.common.constants.AppConstants;
import com.upcraft.stay.common.dto.ApiResponseDTO;
import com.upcraft.stay.common.dto.PageResponseDTO;
import com.upcraft.stay.operations.dto.CreateDocumentRequestDTO;
import com.upcraft.stay.operations.dto.DocumentResponseDTO;
import com.upcraft.stay.operations.enums.DocumentCategory;
import com.upcraft.stay.operations.enums.DocumentScopeType;
import com.upcraft.stay.operations.service.DocumentV1Service;

/**
 * Private multi-scope document registry controller backed by authorized {@code /api/v1/files/{id}/content} downloads.
 */
@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentV1Controller {

    private final DocumentV1Service documentV1Service;

    @GetMapping
    @PreAuthorize("hasAnyRole('PG_OWNER','MANAGER','PG_MEMBER')")
    public ResponseEntity<ApiResponseDTO<PageResponseDTO<DocumentResponseDTO>>> listDocuments(
            Authentication authentication,
            @RequestParam(required = false) UUID propertyId,
            @RequestParam(required = false) DocumentScopeType scopeType,
            @RequestParam(required = false) DocumentCategory documentCategory,
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(required = false) UUID tenancyId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size
    ) {
        return ResponseEntity.ok(ApiResponseDTO.success(
                "Documents loaded",
                documentV1Service.listDocuments(
                        authentication.getName(), propertyId, scopeType, documentCategory,
                        tenantId, tenancyId, page, size
                ),
                MDC.get(AppConstants.TRACE_ID_MDC_KEY)
        ));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('PG_OWNER','MANAGER','PG_MEMBER')")
    public ResponseEntity<ApiResponseDTO<DocumentResponseDTO>> createDocument(
            Authentication authentication,
            @Valid @RequestBody CreateDocumentRequestDTO request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponseDTO.success(
                "Document saved",
                documentV1Service.createDocument(authentication.getName(), request),
                MDC.get(AppConstants.TRACE_ID_MDC_KEY)
        ));
    }

    @DeleteMapping("/{documentId}")
    @PreAuthorize("hasAnyRole('PG_OWNER','MANAGER','PG_MEMBER')")
    public ResponseEntity<Void> deleteDocument(
            Authentication authentication,
            @PathVariable UUID documentId
    ) {
        documentV1Service.deleteDocument(authentication.getName(), documentId);
        return ResponseEntity.noContent().build();
    }
}

