package com.upcraft.stay.room.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.upcraft.stay.room.entity.FloorEntity;

/**
 * Persistence access for property floors.
 */
public interface FloorRepository extends JpaRepository<FloorEntity, UUID> {

    Page<FloorEntity> findAllByPropertyId(UUID propertyId, Pageable pageable);

    Optional<FloorEntity> findByIdAndPropertyId(UUID id, UUID propertyId);

    boolean existsByPropertyIdAndFloorNumber(UUID propertyId, Integer floorNumber);

    boolean existsByPropertyIdAndFloorNumberAndIdNot(UUID propertyId, Integer floorNumber, UUID id);

    boolean existsByPropertyIdAndNameIgnoreCase(UUID propertyId, String name);

    boolean existsByPropertyIdAndNameIgnoreCaseAndIdNot(UUID propertyId, String name, UUID id);

    long countByPropertyId(UUID propertyId);
}

