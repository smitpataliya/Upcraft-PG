package com.upcraft.stay.electricity.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.upcraft.stay.common.enums.ElectricityBillStatus;
import com.upcraft.stay.electricity.entity.ElectricityBillEntity;

/**
 * Persistence access for tenant electricity bill shares and resident photo authorization.
 */
public interface ElectricityBillRepository extends JpaRepository<ElectricityBillEntity, UUID> {

    boolean existsByReadingIdAndBillStatusNot(UUID readingId, ElectricityBillStatus excludedStatus);

    long countByOrganizationId(UUID organizationId);

    List<ElectricityBillEntity> findAllByReadingId(UUID readingId);

    @Query("""
            SELECT COUNT(eb) > 0 FROM ElectricityBillEntity eb
            JOIN eb.reading r
            JOIN eb.tenant t
            WHERE r.photoFile.id = :fileId
              AND eb.billStatus <> :excludedStatus
              AND (t.user.id = :userId
                   OR (t.user IS NULL AND t.organization.id = :organizationId AND LOWER(COALESCE(t.email, '')) = :email))
            """)
    boolean isReadingPhotoAccessibleToResident(
            @Param("fileId") UUID fileId,
            @Param("excludedStatus") ElectricityBillStatus excludedStatus,
            @Param("userId") UUID userId,
            @Param("organizationId") UUID organizationId,
            @Param("email") String email
    );

    @EntityGraph(attributePaths = {"property", "meter", "reading", "reading.photoFile", "tenant", "room", "bed", "invoice"})
    @Query("""
            SELECT eb FROM ElectricityBillEntity eb
            JOIN eb.property p
            JOIN eb.tenant t
            JOIN eb.meter m
            WHERE p.id IN :propertyIds
              AND (:hasTenantFilter = false OR t.id = :tenantId)
              AND (:hasMeterFilter = false OR m.id = :meterId)
              AND (:hasYearFilter = false OR eb.billingYear = :billingYear)
              AND (:hasMonthFilter = false OR eb.billingMonth = :billingMonth)
            """)
    Page<ElectricityBillEntity> findAccessibleBills(
            @Param("propertyIds") Collection<UUID> propertyIds,
            @Param("hasTenantFilter") boolean hasTenantFilter,
            @Param("tenantId") UUID tenantId,
            @Param("hasMeterFilter") boolean hasMeterFilter,
            @Param("meterId") UUID meterId,
            @Param("hasYearFilter") boolean hasYearFilter,
            @Param("billingYear") int billingYear,
            @Param("hasMonthFilter") boolean hasMonthFilter,
            @Param("billingMonth") int billingMonth,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"property", "meter", "reading", "reading.photoFile", "tenant", "room", "bed", "invoice"})
    @Query("""
            SELECT eb FROM ElectricityBillEntity eb
            JOIN eb.tenant t
            WHERE t.user.id = :userId
               OR (t.user IS NULL AND t.organization.id = :organizationId AND LOWER(COALESCE(t.email, '')) = :email)
            """)
    Page<ElectricityBillEntity> findSelfBills(
            @Param("userId") UUID userId,
            @Param("organizationId") UUID organizationId,
            @Param("email") String email,
            Pageable pageable
    );
}

