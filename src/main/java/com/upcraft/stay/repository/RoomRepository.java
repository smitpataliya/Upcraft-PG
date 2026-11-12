package com.upcraft.stay.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.upcraft.stay.domain.entity.Room;

/**
 * Persistence access for legacy and property-scoped PG rooms.
 */
public interface RoomRepository extends JpaRepository<Room, UUID> {

    boolean existsByNumber(String number);

    Optional<Room> findByNumber(String number);

    @EntityGraph(attributePaths = {"floorEntity", "property"})
    Page<Room> findAllByPropertyId(UUID propertyId, Pageable pageable);

    @EntityGraph(attributePaths = {"floorEntity", "property"})
    Optional<Room> findByIdAndPropertyId(UUID id, UUID propertyId);

    boolean existsByPropertyIdAndNumberIgnoreCase(UUID propertyId, String number);

    boolean existsByPropertyIdAndNumberIgnoreCaseAndIdNot(UUID propertyId, String number, UUID id);

    long countByPropertyId(UUID propertyId);

    long countByFloorEntityId(UUID floorId);
}
