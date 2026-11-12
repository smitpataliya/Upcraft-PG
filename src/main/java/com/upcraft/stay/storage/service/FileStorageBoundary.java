package com.upcraft.stay.storage.service;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/**
 * Storage provider boundary isolating local filesystem vs future private cloud object storage (S3/R2).
 */
public interface FileStorageBoundary {

    String save(String storageKey, MultipartFile multipartFile);

    Resource loadAsResource(String storageKey);
}

