package com.upcraft.stay.property.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.upcraft.stay.property.entity.PropertyEntity;

/**
 * Persistence access for PG properties.
 */
public interface PropertyRepository extends JpaRepository<PropertyEntity, UUID> {

    List<PropertyEntity> findAllByOrganizationIdOrderByNameAsc(UUID organizationId);

    Page<PropertyEntity> findAllByOrganizationId(UUID organizationId, Pageable pageable);

    Page<PropertyEntity> findAllByIdIn(Collection<UUID> propertyIds, Pageable pageable);

    Optional<PropertyEntity> findByIdAndOrganizationId(UUID id, UUID organizationId);

    boolean existsByOrganizationIdAndNameIgnoreCase(UUID organizationId, String name);

    boolean existsByOrganizationIdAndNameIgnoreCaseAndIdNot(UUID organizationId, String name, UUID id);
}

