package com.upcraft.stay.storage.service.impl;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.upcraft.stay.common.config.AppProperties;
import com.upcraft.stay.common.exception.ResourceNotFoundException;
import com.upcraft.stay.common.exception.ValidationException;
import com.upcraft.stay.storage.service.FileStorageBoundary;

/**
 * Local disk private storage adapter preventing path traversal outside configured root directory.
 */
@Component
@RequiredArgsConstructor
public class LocalPrivateFileStorageAdapter implements FileStorageBoundary {

    private final AppProperties appProperties;

    @Override
    public String save(String storageKey, MultipartFile multipartFile) {
        Path rootDir = resolveBasePath();
        Path targetPath = rootDir.resolve(storageKey).normalize();
        if (!targetPath.startsWith(rootDir)) {
            throw new ValidationException("Invalid file storage path");
        }
        try {
            Files.createDirectories(targetPath.getParent());
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream rawStream = multipartFile.getInputStream();
                 DigestInputStream digestStream = new DigestInputStream(rawStream, digest)) {
                Files.copy(digestStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Failed to store uploaded file safely", exception);
        }
    }

    @Override
    public Resource loadAsResource(String storageKey) {
        Path rootDir = resolveBasePath();
        Path targetPath = rootDir.resolve(storageKey).normalize();
        if (!targetPath.startsWith(rootDir) || !Files.exists(targetPath)) {
            throw new ResourceNotFoundException("Stored file content not found");
        }
        try {
            return new UrlResource(targetPath.toUri());
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read stored file", exception);
        }
    }

    private Path resolveBasePath() {
        return Path.of(appProperties.getStorage().getLocalBaseDir()).toAbsolutePath().normalize();
    }
}

