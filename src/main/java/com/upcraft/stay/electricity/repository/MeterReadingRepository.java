package com.upcraft.stay.electricity.repository;

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

import com.upcraft.stay.common.enums.MeterReadingStatus;
import com.upcraft.stay.electricity.entity.MeterReadingEntity;

/**
 * Persistence access for electricity meter readings and consumption history.
 */
public interface MeterReadingRepository extends JpaRepository<MeterReadingEntity, UUID> {

    @EntityGraph(attributePaths = {"organization", "property", "meter", "meter.room", "meter.bed", "photoFile",
            "recordedByUser", "approvedByUser"})
    Optional<MeterReadingEntity> findWithDetailsById(UUID id);

    boolean existsByMeterIdAndBillingYearAndBillingMonthAndReadingStatusNotAndCorrectionOfReadingIsNull(
            UUID meterId,
            int billingYear,
            int billingMonth,
            MeterReadingStatus excludedStatus
    );

    List<MeterReadingEntity> findTop6ByMeterIdAndReadingStatusNotOrderByReadingDateDescCreatedAtDesc(
            UUID meterId,
            MeterReadingStatus excludedStatus
    );

    @EntityGraph(attributePaths = {"property", "meter", "meter.room", "meter.bed", "photoFile",
            "recordedByUser", "approvedByUser"})
    Page<MeterReadingEntity> findAllByMeterIdOrderByReadingDateDescCreatedAtDesc(UUID meterId, Pageable pageable);

    @EntityGraph(attributePaths = {"property", "meter", "meter.room", "meter.bed", "photoFile",
            "recordedByUser", "approvedByUser"})
    @Query("""
            SELECT mr FROM MeterReadingEntity mr
            JOIN mr.property p
            JOIN mr.meter m
            WHERE p.id IN :propertyIds
              AND (:hasMeterFilter = false OR m.id = :meterId)
              AND (:hasStatusFilter = false OR mr.readingStatus = :readingStatus)
              AND (:hasYearFilter = false OR mr.billingYear = :billingYear)
              AND (:hasMonthFilter = false OR mr.billingMonth = :billingMonth)
              AND (:unusualOnly = false OR mr.unusualUsage = true)
            """)
    Page<MeterReadingEntity> findAccessibleReadings(
            @Param("propertyIds") Collection<UUID> propertyIds,
            @Param("hasMeterFilter") boolean hasMeterFilter,
            @Param("meterId") UUID meterId,
            @Param("hasStatusFilter") boolean hasStatusFilter,
            @Param("readingStatus") MeterReadingStatus readingStatus,
            @Param("hasYearFilter") boolean hasYearFilter,
            @Param("billingYear") int billingYear,
            @Param("hasMonthFilter") boolean hasMonthFilter,
            @Param("billingMonth") int billingMonth,
            @Param("unusualOnly") boolean unusualOnly,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"meter", "meter.room", "meter.bed"})
    @Query("""
            SELECT mr FROM MeterReadingEntity mr
            WHERE mr.property.id IN :propertyIds
              AND mr.readingStatus <> :excludedStatus
              AND (:hasYearFilter = false OR mr.billingYear = :billingYear)
              AND (:hasMonthFilter = false OR mr.billingMonth = :billingMonth)
            """)
    List<MeterReadingEntity> findReportReadings(
            @Param("propertyIds") Collection<UUID> propertyIds,
            @Param("excludedStatus") MeterReadingStatus excludedStatus,
            @Param("hasYearFilter") boolean hasYearFilter,
            @Param("billingYear") int billingYear,
            @Param("hasMonthFilter") boolean hasMonthFilter,
            @Param("billingMonth") int billingMonth
    );
}

