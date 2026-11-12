package com.upcraft.stay.storage.dto;

import java.time.Instant;
import java.util.UUID;

import com.upcraft.stay.common.enums.FileCategory;

/**
 * Metadata response returned after storing a private upload (never exposes public filesystem paths).
 */
public record StoredFileResponseDTO(
        UUID id,
        UUID propertyId,
        FileCategory fileCategory,
        String originalFilename,
        String contentType,
        Long fileSizeBytes,
        String downloadPath,
        String uploadedByName,
        Instant createdAt
) {}

