package com.upcraft.stay.room.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.upcraft.stay.common.enums.BedStatus;
import com.upcraft.stay.room.entity.BedEntity;

/**
 * Persistence access for PG bed inventory, locking, and vacancy availability search.
 */
public interface BedRepository extends JpaRepository<BedEntity, UUID> {

    @EntityGraph(attributePaths = {"room", "property"})
    Page<BedEntity> findAllByRoomId(UUID roomId, Pageable pageable);

    @EntityGraph(attributePaths = {"room", "property"})
    List<BedEntity> findAllByRoomIdOrderByBedCodeAsc(UUID roomId);

    @EntityGraph(attributePaths = {"room", "property"})
    List<BedEntity> findAllByRoomIdInOrderByBedCodeAsc(Collection<UUID> roomIds);

    @EntityGraph(attributePaths = {"room", "property", "organization"})
    Optional<BedEntity> findWithContextById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM BedEntity b WHERE b.id = :bedId")
    Optional<BedEntity> findLockedById(@Param("bedId") UUID bedId);

    boolean existsByRoomIdAndBedCodeIgnoreCase(UUID roomId, String bedCode);

    boolean existsByRoomIdAndBedCodeIgnoreCaseAndIdNot(UUID roomId, String bedCode, UUID id);

    long countByPropertyId(UUID propertyId);

    long countByPropertyIdAndBedStatus(UUID propertyId, BedStatus bedStatus);

    long countByRoomId(UUID roomId);

    long countByRoomIdAndBedStatus(UUID roomId, BedStatus bedStatus);

    @Query("""
            SELECT b.property.id, b.property.name, b.bedStatus, COUNT(b)
            FROM BedEntity b
            WHERE b.property.id IN :propertyIds
            GROUP BY b.property.id, b.property.name, b.bedStatus
            """)
    List<Object[]> countBedsGroupedByPropertyAndStatus(@Param("propertyIds") Collection<UUID> propertyIds);

    @EntityGraph(attributePaths = {"room", "property", "room.floorEntity"})
    @Query("""
            SELECT b FROM BedEntity b
            JOIN b.room r
            JOIN b.property p
            LEFT JOIN r.floorEntity f
            WHERE p.id IN :propertyIds
              AND (:hasStatusFilter = false OR b.bedStatus = :bedStatus)
              AND (:hasRoomTypeFilter = false OR UPPER(r.roomType) = :roomType)
              AND (:hasMaxRentFilter = false OR b.monthlyRent <= :maxRent)
              AND (:hasAvailableBeforeFilter = false OR b.availableFrom IS NULL OR b.availableFrom <= :availableBefore)
            """)
    Page<BedEntity> findAvailability(
            @Param("propertyIds") Collection<UUID> propertyIds,
            @Param("hasStatusFilter") boolean hasStatusFilter,
            @Param("bedStatus") BedStatus bedStatus,
            @Param("hasRoomTypeFilter") boolean hasRoomTypeFilter,
            @Param("roomType") String roomType,
            @Param("hasMaxRentFilter") boolean hasMaxRentFilter,
            @Param("maxRent") BigDecimal maxRent,
            @Param("hasAvailableBeforeFilter") boolean hasAvailableBeforeFilter,
            @Param("availableBefore") LocalDate availableBefore,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"room", "property"})
    @Query("""
            SELECT b FROM BedEntity b
            JOIN b.room r
            JOIN b.property p
            WHERE p.id IN :propertyIds
              AND (
                  LOWER(b.bedCode) LIKE :pattern
                  OR LOWER(r.number) LIKE :pattern
                  OR LOWER(r.roomType) LIKE :pattern
                  OR LOWER(p.name) LIKE :pattern
              )
            """)
    Page<BedEntity> searchAccessibleBeds(
            @Param("propertyIds") Collection<UUID> propertyIds,
            @Param("pattern") String pattern,
            Pageable pageable
    );
}
