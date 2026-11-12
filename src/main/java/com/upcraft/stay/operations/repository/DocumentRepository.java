package com.upcraft.stay.operations.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.upcraft.stay.operations.entity.DocumentEntity;

/**
 * Persistence access for private organization, property, tenant, tenancy, and billing documents.
 */
public interface DocumentRepository extends JpaRepository<DocumentEntity, UUID>, JpaSpecificationExecutor<DocumentEntity> {

    @Override
    @EntityGraph(attributePaths = {"organization", "property", "storedFile", "tenant", "tenancy", "invoice", "payment", "uploadedByUser"})
    Page<DocumentEntity> findAll(Specification<DocumentEntity> specification, Pageable pageable);

    @EntityGraph(attributePaths = {"organization", "property", "storedFile", "tenant", "tenancy", "invoice", "payment", "uploadedByUser"})
    Optional<DocumentEntity> findWithDetailsById(UUID id);

    @Query("""
            SELECT COUNT(d) > 0 FROM DocumentEntity d
            LEFT JOIN d.tenant t
            WHERE d.storedFile.id = :storedFileId
              AND d.organization.id = :organizationId
              AND d.sharedWithTenant = true
              AND (
                    t.user.id = :userId
                    OR (t.user IS NULL AND LOWER(COALESCE(t.email, '')) = :email)
                    OR d.scopeType = com.upcraft.stay.operations.enums.DocumentScopeType.PROPERTY
                    OR d.scopeType = com.upcraft.stay.operations.enums.DocumentScopeType.ORGANIZATION
              )
            """)
    boolean isDocumentFileAccessibleToResident(
            @Param("storedFileId") UUID storedFileId,
            @Param("userId") UUID userId,
            @Param("organizationId") UUID organizationId,
            @Param("email") String email
    );
}

