package com.upcraft.stay.storage.service;

import java.util.UUID;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import com.upcraft.stay.common.enums.FileCategory;
import com.upcraft.stay.storage.dto.StoredFileResponseDTO;

/**
 * Service handling validated private file uploads and authorization-guarded content retrieval.
 */
public interface StoredFileService {

    StoredFileResponseDTO uploadPrivateFile(
            String principalEmail,
            UUID propertyId,
            FileCategory category,
            MultipartFile multipartFile
    );

    StoredFileDownload loadAuthorizedFile(String principalEmail, UUID fileId);

    /**
     * Download payload combining validated metadata and readable stream resource.
     */
    record StoredFileDownload(
            String originalFilename,
            String contentType,
            Resource resource
    ) {}
}

