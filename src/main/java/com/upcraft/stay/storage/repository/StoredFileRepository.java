package com.upcraft.stay.storage.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.upcraft.stay.storage.entity.StoredFileEntity;

/**
 * Persistence access for private stored file metadata records.
 */
public interface StoredFileRepository extends JpaRepository<StoredFileEntity, UUID> {

    @EntityGraph(attributePaths = {"organization", "property", "uploadedByUser"})
    Optional<StoredFileEntity> findWithDetailsById(UUID id);
}

