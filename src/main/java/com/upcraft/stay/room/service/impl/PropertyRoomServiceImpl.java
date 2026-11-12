package com.upcraft.stay.room.service.impl;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.upcraft.stay.common.config.AppProperties;
import com.upcraft.stay.common.constants.AppConstants;
import com.upcraft.stay.common.dto.PageResponseDTO;
import com.upcraft.stay.common.enums.BedStatus;
import com.upcraft.stay.common.enums.RoomStatus;
import com.upcraft.stay.common.enums.TenancyStatus;
import com.upcraft.stay.common.exception.ConflictException;
import com.upcraft.stay.common.exception.ForbiddenException;
import com.upcraft.stay.common.exception.ResourceNotFoundException;
import com.upcraft.stay.common.exception.ValidationException;
import com.upcraft.stay.common.util.PaginationUtils;
import com.upcraft.stay.domain.entity.Room;
import com.upcraft.stay.domain.entity.User;
import com.upcraft.stay.domain.enums.Role;
import com.upcraft.stay.property.entity.PropertyEntity;
import com.upcraft.stay.repository.RoomRepository;
import com.upcraft.stay.room.dto.CreatePropertyRoomRequestDTO;
import com.upcraft.stay.room.dto.PropertyRoomResponseDTO;
import com.upcraft.stay.room.dto.UpdatePropertyRoomRequestDTO;
import com.upcraft.stay.room.entity.BedEntity;
import com.upcraft.stay.room.entity.FloorEntity;
import com.upcraft.stay.room.mapper.PropertyRoomAssembler;
import com.upcraft.stay.room.repository.BedRepository;
import com.upcraft.stay.room.repository.FloorRepository;
import com.upcraft.stay.room.service.PropertyRoomService;
import com.upcraft.stay.security.AccessGuardService;
import com.upcraft.stay.tenant.entity.TenancyEntity;
import com.upcraft.stay.tenant.repository.TenancyRepository;

/**
 * Property-scoped room service maintaining synchronized bed inventory and legacy room counts.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PropertyRoomServiceImpl implements PropertyRoomService {

    private static final Set<String> SORT_FIELDS = Set.of("number", "floor", "createdAt");
    private static final List<TenancyStatus> BLOCKING_ROOM_DELETE_STATUSES = List.of(
            TenancyStatus.ACTIVE,
            TenancyStatus.NOTICE_PERIOD,
            TenancyStatus.RESERVED
    );

    private final AccessGuardService accessGuardService;
    private final RoomRepository roomRepository;
    private final FloorRepository floorRepository;
    private final BedRepository bedRepository;
    private final TenancyRepository tenancyRepository;
    private final PropertyRoomAssembler roomAssembler;
    private final AppProperties appProperties;

    @Override
    @Transactional(readOnly = true)
    public PageResponseDTO<PropertyRoomResponseDTO> listPropertyRooms(
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
                page, size, sortBy, sortDirection, SORT_FIELDS, "number", appProperties
        );
        Page<Room> roomPage = roomRepository.findAllByPropertyId(propertyId, pageable);
        List<UUID> roomIds = roomPage.getContent().stream().map(Room::getId).toList();
        if (CollectionUtils.isEmpty(roomIds)) {
            return PageResponseDTO.from(roomPage.map(room -> roomAssembler.toRoomResponse(room, List.of(), Map.of())));
        }

        Map<UUID, List<BedEntity>> bedsByRoom = bedRepository.findAllByRoomIdInOrderByBedCodeAsc(roomIds)
                .stream()
                .collect(Collectors.groupingBy(bed -> bed.getRoom().getId()));
        Map<UUID, TenancyEntity> activeTenancyByBed = roomAssembler.loadActiveTenancyByBed(roomIds);

        return PageResponseDTO.from(roomPage.map(room -> roomAssembler.toRoomResponse(
                room, bedsByRoom.getOrDefault(room.getId(), List.of()), activeTenancyByBed
        )));
    }

    @Override
    @Transactional(readOnly = true)
    public PropertyRoomResponseDTO getPropertyRoom(String principalEmail, UUID propertyId, UUID roomId) {
        User currentUser = accessGuardService.loadActiveUser(principalEmail);
        accessGuardService.requirePropertyManagementAccess(currentUser, propertyId, "READ");
        Room room = findRoomInProperty(propertyId, roomId);
        List<BedEntity> beds = bedRepository.findAllByRoomIdOrderByBedCodeAsc(roomId);
        return roomAssembler.toRoomResponse(room, beds, roomAssembler.loadActiveTenancyByBed(List.of(roomId)));
    }

    @Override
    @Transactional
    public PropertyRoomResponseDTO createPropertyRoom(
            String principalEmail,
            UUID propertyId,
            CreatePropertyRoomRequestDTO createRoomRequestDTO
    ) {
        User currentUser = accessGuardService.loadActiveUser(principalEmail);
        requireOwnerRole(currentUser);
        PropertyEntity propertyEntity = accessGuardService.requirePropertyManagementAccess(
                currentUser, propertyId, "READ"
        );
        String cleanNumber = createRoomRequestDTO.number().trim();
        if (roomRepository.existsByPropertyIdAndNumberIgnoreCase(propertyId, cleanNumber)) {
            throw new ConflictException("Room number already exists in this property");
        }

        Optional<FloorEntity> floorOptional = resolveOptionalFloor(propertyId, Optional.ofNullable(createRoomRequestDTO.floorId()));
        Room savedRoom = roomRepository.save(roomAssembler.buildNewRoom(
                propertyEntity, floorOptional, createRoomRequestDTO, cleanNumber
        ));
        List<BedEntity> createdBeds = Boolean.FALSE.equals(createRoomRequestDTO.autoCreateBeds())
                ? List.of()
                : createDefaultBeds(savedRoom, createRoomRequestDTO.capacity());
        log.info("Property room created. roomId={}, propertyId={}, bedCount={}",
                savedRoom.getId(), propertyId, createdBeds.size());
        return roomAssembler.toRoomResponse(savedRoom, createdBeds, Map.of());
    }

    @Override
    @Transactional
    public PropertyRoomResponseDTO updatePropertyRoom(
            String principalEmail,
            UUID propertyId,
            UUID roomId,
            UpdatePropertyRoomRequestDTO updateRoomRequestDTO
    ) {
        User currentUser = accessGuardService.loadActiveUser(principalEmail);
        requireOwnerRole(currentUser);
        accessGuardService.requirePropertyManagementAccess(currentUser, propertyId, "READ");
        Room room = findRoomInProperty(propertyId, roomId);
        String cleanNumber = updateRoomRequestDTO.number().trim();
        if (roomRepository.existsByPropertyIdAndNumberIgnoreCaseAndIdNot(propertyId, cleanNumber, roomId)) {
            throw new ConflictException("Another room with this number already exists in this property");
        }
        if (updateRoomRequestDTO.capacity() < room.getOccupied()) {
            throw new ValidationException("Room capacity cannot be reduced below currently occupied beds");
        }

        Optional<FloorEntity> floorOptional = resolveOptionalFloor(propertyId, Optional.ofNullable(updateRoomRequestDTO.floorId()));
        roomAssembler.applyRoomUpdates(room, floorOptional, updateRoomRequestDTO, cleanNumber);
        Room savedRoom = roomRepository.save(room);

        List<BedEntity> beds = bedRepository.findAllByRoomIdOrderByBedCodeAsc(roomId);
        return roomAssembler.toRoomResponse(savedRoom, beds, roomAssembler.loadActiveTenancyByBed(List.of(roomId)));
    }

    @Override
    @Transactional
    public void deletePropertyRoom(String principalEmail, UUID propertyId, UUID roomId) {
        User currentUser = accessGuardService.loadActiveUser(principalEmail);
        requireOwnerRole(currentUser);
        accessGuardService.requirePropertyManagementAccess(currentUser, propertyId, "READ");
        Room room = findRoomInProperty(propertyId, roomId);
        if (tenancyRepository.existsByRoomIdAndTenancyStatusIn(roomId, BLOCKING_ROOM_DELETE_STATUSES)) {
            throw new ConflictException("Cannot delete room while active, notice-period, or reserved tenancies exist");
        }

        List<BedEntity> roomBeds = bedRepository.findAllByRoomIdOrderByBedCodeAsc(roomId);
        roomBeds.forEach(bedEntity -> bedEntity.markDeleted(currentUser.getEmail(), null));
        bedRepository.saveAll(roomBeds);

        room.setRecordStatus(AppConstants.STATUS_DELETED);
        room.setRoomStatus(RoomStatus.ARCHIVED);
        room.setDeletedAt(LocalDateTime.now());
        room.setDeletedBy(currentUser.getEmail());
        roomRepository.save(room);
        log.info("Room archived/deleted. roomId={}, propertyId={}", roomId, propertyId);
    }

    private Room findRoomInProperty(UUID propertyId, UUID roomId) {
        return roomRepository.findByIdAndPropertyId(roomId, propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found in this property"));
    }

    private Optional<FloorEntity> resolveOptionalFloor(UUID propertyId, Optional<UUID> floorIdOptional) {
        return floorIdOptional.map(floorId -> floorRepository.findByIdAndPropertyId(floorId, propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Floor not found in this property")));
    }

    private List<BedEntity> createDefaultBeds(Room room, int count) {
        List<BedEntity> bedsToCreate = IntStream.rangeClosed(1, count)
                .mapToObj(index -> {
                    BedEntity bed = new BedEntity();
                    bed.setOrganization(room.getProperty().getOrganization());
                    bed.setProperty(room.getProperty());
                    bed.setRoom(room);
                    bed.setBedCode("BED-" + index);
                    bed.setMonthlyRent(Optional.ofNullable(room.getBaseMonthlyRent()).orElse(BigDecimal.ZERO));
                    bed.setDefaultDeposit(Optional.ofNullable(room.getDefaultDeposit()).orElse(BigDecimal.ZERO));
                    bed.setBedStatus(BedStatus.VACANT);
                    return bed;
                })
                .toList();
        return bedRepository.saveAll(bedsToCreate);
    }

    private void requireOwnerRole(User currentUser) {
        if (currentUser.getRole() != Role.PG_OWNER) {
            throw new ForbiddenException("Only PG owners can create, edit, or archive rooms");
        }
    }
}
