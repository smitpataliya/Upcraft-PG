package com.upcraft.stay.operations.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.upcraft.stay.operations.entity.ManagerTaskEntity;

/**
 * Persistence access for property-scoped manager tasks.
 */
public interface ManagerTaskRepository extends JpaRepository<ManagerTaskEntity, UUID>, JpaSpecificationExecutor<ManagerTaskEntity> {

    @Override
    @EntityGraph(attributePaths = {"organization", "property", "assignedToUser", "createdByUser", "complaint"})
    Page<ManagerTaskEntity> findAll(Specification<ManagerTaskEntity> specification, Pageable pageable);

    @EntityGraph(attributePaths = {"organization", "property", "assignedToUser", "createdByUser", "complaint"})
    Optional<ManagerTaskEntity> findWithDetailsById(UUID id);
}

