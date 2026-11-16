package com.upcraft.stay.audit.repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.upcraft.stay.audit.entity.AuditLogEntity;

/**
 * Append-and-read persistence interface for immutable audit log entries.
 */
public interface AuditLogRepository extends JpaRepository<AuditLogEntity, UUID>, JpaSpecificationExecutor<AuditLogEntity> {

    @EntityGraph(attributePaths = {"organization", "property", "actorUser"})
    Page<AuditLogEntity> findAll(Specification<AuditLogEntity> specification, Pageable pageable);
}

