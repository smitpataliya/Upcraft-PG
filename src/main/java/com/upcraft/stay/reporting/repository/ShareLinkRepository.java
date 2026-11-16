package com.upcraft.stay.reporting.repository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.upcraft.stay.reporting.entity.ShareLinkEntity;

/**
 * Persistence access for hashed expiring share links.
 */
public interface ShareLinkRepository extends JpaRepository<ShareLinkEntity, UUID> {

    @EntityGraph(attributePaths = {"organization", "property", "createdByUser"})
    Optional<ShareLinkEntity> findByTokenHash(String tokenHash);

    @EntityGraph(attributePaths = {"organization", "property", "createdByUser"})
    Optional<ShareLinkEntity> findWithDetailsById(UUID id);

    @EntityGraph(attributePaths = {"organization", "property", "createdByUser"})
    Page<ShareLinkEntity> findAllByOrganizationIdAndPropertyIdInOrderByCreatedAtDesc(
            UUID organizationId,
            Collection<UUID> propertyIds,
            Pageable pageable
    );
}
