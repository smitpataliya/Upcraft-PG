package com.upcraft.stay.tenant.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.upcraft.stay.common.enums.TenantStatus;
import com.upcraft.stay.tenant.entity.TenantEntity;

/**
 * Persistence access for property-scoped tenant directory records.
 */
public interface TenantRepository extends JpaRepository<TenantEntity, UUID> {

    @EntityGraph(attributePaths = {"property", "organization", "user"})
    Optional<TenantEntity> findWithDetailsById(UUID id);

    @EntityGraph(attributePaths = {"property"})
    List<TenantEntity> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<TenantEntity> findFirstByPropertyIdAndEmailIgnoreCase(UUID propertyId, String email);

    boolean existsByPropertyIdAndEmailIgnoreCaseAndTenantStatusIn(
            UUID propertyId,
            String email,
            Collection<TenantStatus> statuses
    );

    @EntityGraph(attributePaths = {"property"})
    @Query("""
            SELECT t FROM TenantEntity t
            JOIN t.property p
            WHERE p.id IN :propertyIds
              AND (:hasStatusFilter = false OR t.tenantStatus = :tenantStatus)
              AND (
                  :hasSearch = false
                  OR LOWER(t.fullName) LIKE :searchPattern
                  OR LOWER(t.phone) LIKE :searchPattern
                  OR LOWER(COALESCE(t.email, '')) LIKE :searchPattern
              )
            """)
    Page<TenantEntity> searchAccessibleTenants(
            @Param("propertyIds") Collection<UUID> propertyIds,
            @Param("hasStatusFilter") boolean hasStatusFilter,
            @Param("tenantStatus") TenantStatus tenantStatus,
            @Param("hasSearch") boolean hasSearch,
            @Param("searchPattern") String searchPattern,
            Pageable pageable
    );
}
