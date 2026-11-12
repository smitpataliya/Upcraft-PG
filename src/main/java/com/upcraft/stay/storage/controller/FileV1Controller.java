package com.upcraft.stay.storage.controller;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.upcraft.stay.common.constants.AppConstants;
import com.upcraft.stay.common.dto.ApiResponseDTO;
import com.upcraft.stay.common.enums.FileCategory;
import com.upcraft.stay.storage.dto.StoredFileResponseDTO;
import com.upcraft.stay.storage.service.StoredFileService;

/**
 * Private file upload and authorization-guarded content retrieval controller.
 */
@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class FileV1Controller {

    private final StoredFileService storedFileService;

    /**
     * Uploads a validated image or PDF attachment into private storage.
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('PG_OWNER','MANAGER','PG_MEMBER')")
    public ResponseEntity<ApiResponseDTO<StoredFileResponseDTO>> uploadFile(
            Authentication authentication,
            @RequestParam(required = false) UUID propertyId,
            @RequestParam(defaultValue = "OTHER") FileCategory category,
            @RequestParam("file") MultipartFile file
    ) {
        StoredFileResponseDTO created = storedFileService.uploadPrivateFile(
                authentication.getName(), propertyId, category, file
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseDTO.success(
                        "File uploaded to private storage",
                        created,
                        MDC.get(AppConstants.TRACE_ID_MDC_KEY)
                ));
    }

    /**
     * Streams a private stored file after checking organization, property, and tenant access.
     */
    @GetMapping("/{fileId}/content")
    @PreAuthorize("hasAnyRole('PG_OWNER','MANAGER','PG_MEMBER')")
    public ResponseEntity<Resource> downloadFile(
            Authentication authentication,
            @PathVariable UUID fileId
    ) {
        StoredFileService.StoredFileDownload payload = storedFileService.loadAuthorizedFile(
                authentication.getName(), fileId
        );
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(payload.originalFilename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(payload.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(payload.resource());
    }
}
