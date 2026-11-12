package com.upcraft.stay.electricity.repository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.upcraft.stay.common.enums.MeterScope;
import com.upcraft.stay.common.enums.MeterStatus;
import com.upcraft.stay.electricity.entity.MeterEntity;

/**
 * Persistence access for property electricity meters.
 */
public interface MeterRepository extends JpaRepository<MeterEntity, UUID> {

    @EntityGraph(attributePaths = {"organization", "property", "room", "bed"})
    Optional<MeterEntity> findWithDetailsById(UUID id);

    boolean existsByPropertyIdAndMeterNumberIgnoreCase(UUID propertyId, String meterNumber);

    boolean existsByPropertyIdAndMeterNumberIgnoreCaseAndIdNot(UUID propertyId, String meterNumber, UUID excludedId);

    @EntityGraph(attributePaths = {"property", "room", "bed"})
    @Query("""
            SELECT m FROM MeterEntity m
            JOIN m.property p
            LEFT JOIN m.room r
            LEFT JOIN m.bed b
            WHERE p.id IN :propertyIds
              AND (:hasScopeFilter = false OR m.meterScope = :meterScope)
              AND (:hasStatusFilter = false OR m.meterStatus = :meterStatus)
              AND (:hasSearch = false
                   OR LOWER(m.meterNumber) LIKE :searchPattern
                   OR LOWER(COALESCE(m.meterLabel, '')) LIKE :searchPattern
                   OR LOWER(COALESCE(r.number, '')) LIKE :searchPattern
                   OR LOWER(COALESCE(b.bedCode, '')) LIKE :searchPattern)
            """)
    Page<MeterEntity> findAccessibleMeters(
            @Param("propertyIds") Collection<UUID> propertyIds,
            @Param("hasScopeFilter") boolean hasScopeFilter,
            @Param("meterScope") MeterScope meterScope,
            @Param("hasStatusFilter") boolean hasStatusFilter,
            @Param("meterStatus") MeterStatus meterStatus,
            @Param("hasSearch") boolean hasSearch,
            @Param("searchPattern") String searchPattern,
            Pageable pageable
    );
}

