package com.upcraft.stay.organization.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.upcraft.stay.organization.entity.ManagerPropertyAssignmentEntity;

/**
 * Persistence access for manager-to-property authorizations.
 */
public interface ManagerPropertyAssignmentRepository extends JpaRepository<ManagerPropertyAssignmentEntity, UUID> {

    @EntityGraph(attributePaths = {"property", "managerUser"})
    List<ManagerPropertyAssignmentEntity> findAllByManagerUserIdAndStatus(UUID managerUserId, Short status);

    @EntityGraph(attributePaths = {"property", "managerUser"})
    Page<ManagerPropertyAssignmentEntity> findAllByOrganizationId(UUID organizationId, Pageable pageable);

    Optional<ManagerPropertyAssignmentEntity> findByPropertyIdAndManagerUserId(UUID propertyId, UUID managerUserId);

    boolean existsByPropertyIdAndManagerUserIdAndStatus(UUID propertyId, UUID managerUserId, Short status);
}

