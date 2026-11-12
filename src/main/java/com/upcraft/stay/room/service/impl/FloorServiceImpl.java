package com.upcraft.stay.room.service.impl;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.upcraft.stay.common.config.AppProperties;
import com.upcraft.stay.common.dto.PageResponseDTO;
import com.upcraft.stay.common.exception.ConflictException;
import com.upcraft.stay.common.exception.ForbiddenException;
import com.upcraft.stay.common.exception.ResourceNotFoundException;
import com.upcraft.stay.common.util.PaginationUtils;
import com.upcraft.stay.domain.entity.User;
import com.upcraft.stay.domain.enums.Role;
import com.upcraft.stay.property.entity.PropertyEntity;
import com.upcraft.stay.repository.RoomRepository;
import com.upcraft.stay.room.dto.CreateFloorRequestDTO;
import com.upcraft.stay.room.dto.FloorResponseDTO;
import com.upcraft.stay.room.dto.UpdateFloorRequestDTO;
import com.upcraft.stay.room.entity.FloorEntity;
import com.upcraft.stay.room.mapper.FloorMapper;
import com.upcraft.stay.room.repository.FloorRepository;
import com.upcraft.stay.room.service.FloorService;
import com.upcraft.stay.security.AccessGuardService;

/**
 * Property-scoped floor CRUD service enforcing active uniqueness and room cleanup before archival.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FloorServiceImpl implements FloorService {

    private static final Set<String> SORT_FIELDS = Set.of("floorNumber", "name", "createdAt");

    private final AccessGuardService accessGuardService;
    private final FloorRepository floorRepository;
    private final RoomRepository roomRepository;
    private final FloorMapper floorMapper;
    private final AppProperties appProperties;

    @Override
    @Transactional(readOnly = true)
    public PageResponseDTO<FloorResponseDTO> listFloors(
            String principalEmail,
            UUID propertyId,
            int page,
            Integer size,
            String sortBy,
            String sortDirection
    ) {
        User currentUser = accessGuardService.loadActiveUser(principalEmail);
        accessGuardService.requirePropertyManagementAccess(currentUser, propertyId, "READ");
        Pageable pageable = PaginationUtils.createPageable(
                page, size, sortBy, sortDirection, SORT_FIELDS, "floorNumber", appProperties
        );
        Page<FloorResponseDTO> floorPage = floorRepository.findAllByPropertyId(propertyId, pageable)
                .map(floorEntity -> floorMapper.toFloorResponseDTO(
                        floorEntity, roomRepository.countByFloorEntityId(floorEntity.getId())
                ));
        return PageResponseDTO.from(floorPage);
    }

    @Override
    @Transactional
    public FloorResponseDTO createFloor(
            String principalEmail,
            UUID propertyId,
            CreateFloorRequestDTO createFloorRequestDTO
    ) {
        User currentUser = accessGuardService.loadActiveUser(principalEmail);
        requireOwnerRole(currentUser);
        PropertyEntity propertyEntity = accessGuardService.requirePropertyManagementAccess(
                currentUser, propertyId, "READ"
        );
        String cleanName = createFloorRequestDTO.name().trim();
        validateUniqueFloorAttributes(propertyId, createFloorRequestDTO.floorNumber(), cleanName, Optional.empty());

        FloorEntity floorEntity = new FloorEntity();
        floorEntity.setOrganization(propertyEntity.getOrganization());
        floorEntity.setProperty(propertyEntity);
        floorEntity.setFloorNumber(createFloorRequestDTO.floorNumber());
        floorEntity.setName(cleanName);
        floorEntity.setNotes(StringUtils.trimToNull(createFloorRequestDTO.notes()));

        FloorEntity savedFloor = floorRepository.save(floorEntity);
        log.info("Floor created. floorId={}, propertyId={}", savedFloor.getId(), propertyId);
        return floorMapper.toFloorResponseDTO(savedFloor, 0L);
    }

    @Override
    @Transactional
    public FloorResponseDTO updateFloor(
            String principalEmail,
            UUID propertyId,
            UUID floorId,
            UpdateFloorRequestDTO updateFloorRequestDTO
    ) {
        User currentUser = accessGuardService.loadActiveUser(principalEmail);
        requireOwnerRole(currentUser);
        accessGuardService.requirePropertyManagementAccess(currentUser, propertyId, "READ");
        FloorEntity floorEntity = floorRepository.findByIdAndPropertyId(floorId, propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Floor not found for this property"));

        String cleanName = updateFloorRequestDTO.name().trim();
        validateUniqueFloorAttributes(propertyId, updateFloorRequestDTO.floorNumber(), cleanName, Optional.of(floorId));

        floorEntity.setFloorNumber(updateFloorRequestDTO.floorNumber());
        floorEntity.setName(cleanName);
        floorEntity.setNotes(StringUtils.trimToNull(updateFloorRequestDTO.notes()));
        FloorEntity savedFloor = floorRepository.save(floorEntity);
        long roomCount = roomRepository.countByFloorEntityId(floorId);
        return floorMapper.toFloorResponseDTO(savedFloor, roomCount);
    }

    @Override
    @Transactional
    public void deleteFloor(String principalEmail, UUID propertyId, UUID floorId) {
        User currentUser = accessGuardService.loadActiveUser(principalEmail);
        requireOwnerRole(currentUser);
        accessGuardService.requirePropertyManagementAccess(currentUser, propertyId, "READ");
        FloorEntity floorEntity = floorRepository.findByIdAndPropertyId(floorId, propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Floor not found for this property"));
        if (roomRepository.countByFloorEntityId(floorId) > 0) {
            throw new ConflictException("Remove or reassign active rooms before deleting this floor");
        }
        floorEntity.markDeleted(currentUser.getEmail(), null);
        floorRepository.save(floorEntity);
        log.info("Floor soft-deleted. floorId={}, propertyId={}", floorId, propertyId);
    }

    private void requireOwnerRole(User currentUser) {
        if (currentUser.getRole() != Role.PG_OWNER) {
            throw new ForbiddenException("Only PG owners can create, edit, or archive floors");
        }
    }

    private void validateUniqueFloorAttributes(
            UUID propertyId,
            Integer floorNumber,
            String cleanName,
            Optional<UUID> excludedFloorId
    ) {
        boolean numberExists = excludedFloorId
                .map(id -> floorRepository.existsByPropertyIdAndFloorNumberAndIdNot(propertyId, floorNumber, id))
                .orElseGet(() -> floorRepository.existsByPropertyIdAndFloorNumber(propertyId, floorNumber));
        if (numberExists) {
            throw new ConflictException("Floor number already exists in this property");
        }
        boolean nameExists = excludedFloorId
                .map(id -> floorRepository.existsByPropertyIdAndNameIgnoreCaseAndIdNot(propertyId, cleanName, id))
                .orElseGet(() -> floorRepository.existsByPropertyIdAndNameIgnoreCase(propertyId, cleanName));
        if (nameExists) {
            throw new ConflictException("Floor name already exists in this property");
        }
    }
}
