package com.upcraft.stay.tenant.repository;

import java.time.LocalDate;
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

import com.upcraft.stay.common.enums.TenancyStatus;
import com.upcraft.stay.tenant.entity.TenancyEntity;

/**
 * Persistence access for bed allocation history and active tenancy constraints.
 */
public interface TenancyRepository extends JpaRepository<TenancyEntity, UUID> {

    @EntityGraph(attributePaths = {"tenant", "property", "room", "bed"})
    Optional<TenancyEntity> findWithDetailsById(UUID id);

    boolean existsByBedIdAndTenancyStatusIn(UUID bedId, Collection<TenancyStatus> statuses);

    boolean existsByPropertyIdAndTenancyStatusIn(UUID propertyId, Collection<TenancyStatus> statuses);

    boolean existsByRoomIdAndTenancyStatusIn(UUID roomId, Collection<TenancyStatus> statuses);

    boolean existsByTenantIdAndTenancyStatusIn(UUID tenantId, Collection<TenancyStatus> statuses);

    @EntityGraph(attributePaths = {"tenant", "property", "room", "bed"})
    Optional<TenancyEntity> findFirstByBedIdAndTenancyStatusIn(UUID bedId, Collection<TenancyStatus> statuses);

    @EntityGraph(attributePaths = {"tenant", "room", "bed"})
    List<TenancyEntity> findAllByRoomIdInAndTenancyStatusIn(Collection<UUID> roomIds, Collection<TenancyStatus> statuses);

    @EntityGraph(attributePaths = {"tenant", "room", "bed"})
    List<TenancyEntity> findAllByTenantIdInAndTenancyStatusIn(Collection<UUID> tenantIds, Collection<TenancyStatus> statuses);

    @EntityGraph(attributePaths = {"tenant", "property", "room", "bed"})
    Page<TenancyEntity> findAllByBedId(UUID bedId, Pageable pageable);

    @EntityGraph(attributePaths = {"tenant", "property", "room", "bed"})
    List<TenancyEntity> findAllByTenantIdOrderByMoveInDateDescCreatedAtDesc(UUID tenantId);

    @EntityGraph(attributePaths = {"tenant", "property", "room", "bed"})
    List<TenancyEntity> findAllByTenantUserIdOrderByMoveInDateDescCreatedAtDesc(UUID userId);

    @EntityGraph(attributePaths = {"tenant", "property", "room", "bed"})
    @Query("""
            SELECT tn FROM TenancyEntity tn
            JOIN tn.tenant t
            WHERE t.user.id = :userId
               OR (t.user IS NULL AND t.organization.id = :organizationId AND LOWER(COALESCE(t.email, '')) = :email)
            ORDER BY tn.moveInDate DESC, tn.createdAt DESC
            """)
    List<TenancyEntity> findSelfResidencyTenancies(
            @Param("userId") UUID userId,
            @Param("organizationId") UUID organizationId,
            @Param("email") String email
    );

    @EntityGraph(attributePaths = {"tenant", "property", "room", "bed"})
    @Query("""
            SELECT tn FROM TenancyEntity tn
            JOIN tn.tenant t
            JOIN tn.property p
            WHERE p.id IN :propertyIds
              AND (:hasStatusFilter = false OR tn.tenancyStatus = :tenancyStatus)
              AND (:hasTenantFilter = false OR t.id = :tenantId)
            """)
    Page<TenancyEntity> findAccessibleTenancies(
            @Param("propertyIds") Collection<UUID> propertyIds,
            @Param("hasStatusFilter") boolean hasStatusFilter,
            @Param("tenancyStatus") TenancyStatus tenancyStatus,
            @Param("hasTenantFilter") boolean hasTenantFilter,
            @Param("tenantId") UUID tenantId,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"organization", "property", "tenant", "room", "bed"})
    @Query("""
            SELECT tn FROM TenancyEntity tn
            WHERE tn.property.id IN :propertyIds
              AND tn.tenancyStatus IN :eligibleStatuses
              AND tn.moveInDate <= :periodEnd
              AND (tn.actualMoveOutDate IS NULL OR tn.actualMoveOutDate >= :periodStart)
            ORDER BY tn.property.name ASC, tn.room.number ASC, tn.bed.bedCode ASC
            """)
    List<TenancyEntity> findBillableTenanciesForPeriod(
            @Param("propertyIds") Collection<UUID> propertyIds,
            @Param("eligibleStatuses") Collection<TenancyStatus> eligibleStatuses,
            @Param("periodStart") LocalDate periodStart,
            @Param("periodEnd") LocalDate periodEnd
    );
}
