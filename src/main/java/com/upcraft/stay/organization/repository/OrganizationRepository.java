package com.upcraft.stay.organization.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.upcraft.stay.organization.entity.OrganizationEntity;

/**
 * Persistence access for PG operator organizations.
 */
public interface OrganizationRepository extends JpaRepository<OrganizationEntity, UUID> {

    Optional<OrganizationEntity> findFirstByOwnerUserIdOrderByCreatedAtAsc(UUID ownerUserId);

    boolean existsBySlug(String slug);
}

