package com.upcraft.stay.room.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.upcraft.stay.common.config.AppProperties;
import com.upcraft.stay.common.dto.PageResponseDTO;
import com.upcraft.stay.common.enums.BedStatus;
import com.upcraft.stay.common.enums.TenancyStatus;
import com.upcraft.stay.common.exception.ConflictException;
import com.upcraft.stay.common.exception.ForbiddenException;
import com.upcraft.stay.common.exception.ResourceNotFoundException;
import com.upcraft.stay.common.exception.ValidationException;
import com.upcraft.stay.common.util.PaginationUtils;
import com.upcraft.stay.domain.entity.Room;
import com.upcraft.stay.domain.entity.User;
import com.upcraft.stay.domain.enums.Role;
import com.upcraft.stay.repository.RoomRepository;
import com.upcraft.stay.room.dto.BedAvailabilityResponseDTO;
import com.upcraft.stay.room.dto.BedResponseDTO;
import com.upcraft.stay.room.dto.CreateBedRequestDTO;
import com.upcraft.stay.room.dto.UpdateBedRequestDTO;
import com.upcraft.stay.room.dto.UpdateBedStatusRequestDTO;
import com.upcraft.stay.room.entity.BedEntity;
import com.upcraft.stay.room.mapper.BedMapper;
import com.upcraft.stay.room.mapper.PropertyRoomAssembler;
import com.upcraft.stay.room.repository.BedRepository;
import com.upcraft.stay.room.service.BedService;
import com.upcraft.stay.security.AccessGuardService;
import com.upcraft.stay.tenant.dto.TenancyResponseDTO;
import com.upcraft.stay.tenant.entity.TenancyEntity;
import com.upcraft.stay.tenant.mapper.TenancyMapper;
import com.upcraft.stay.tenant.repository.TenancyRepository;

/**
 * Property-scoped bed service managing vacancy searches, safe status transitions, and history.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BedServiceImpl implements BedService {

    private static final Set<String> BED_SORT_FIELDS = Set.of("bedCode", "monthlyRent", "createdAt", "bedStatus");
    private static final Set<String> HISTORY_SORT_FIELDS = Set.of("moveInDate", "createdAt");
    private static final List<TenancyStatus> ACTIVE_TENANCY_STATUSES = List.of(
            TenancyStatus.ACTIVE,
            TenancyStatus.NOTICE_PERIOD
    );

    private final AccessGuardService accessGuardService;
    private final RoomRepository roomRepository;
    private final BedRepository bedRepository;
    private final TenancyRepository tenancyRepository;
    private final BedMapper bedMapper;
    private final PropertyRoomAssembler roomAssembler;
    private final TenancyMapper tenancyMapper;
    private final AppProperties appProperties;

    @Override
    @Transactional(readOnly = true)
    public PageResponseDTO<BedResponseDTO> listRoomBeds(
            String principalEmail,
            UUID roomId,
            int page,
            Integer size,
            String sortBy,
            String sortDirection
    ) {
        User currentUser = accessGuardService.loadActiveUser(principalEmail);
        Room room = findPropertyScopedRoom(roomId);
        accessGuardService.requirePropertyManagementAccess(currentUser, room.getProperty().getId(), "READ");
        Pageable pageable = PaginationUtils.createPageable(
                page, size, sortBy, sortDirection, BED_SORT_FIELDS, "bedCode", appProperties
        );
        Map<UUID, TenancyEntity> activeTenancyByBed = roomAssembler.loadActiveTenancyByBed(List.of(roomId));
        return PageResponseDTO.from(bedRepository.findAllByRoomId(roomId, pageable)
                .map(bedEntity -> roomAssembler.toBedDTO(bedEntity, activeTenancyByBed)));
    }

    @Override
    @Transactional
    public BedResponseDTO createBed(String principalEmail, UUID roomId, CreateBedRequestDTO createBedRequestDTO) {
        User currentUser = accessGuardService.loadActiveUser(principalEmail);
        if (currentUser.getRole() != Role.PG_OWNER) {
            throw new ForbiddenException("Only PG owners can create beds");
        }
        Room room = findPropertyScopedRoom(roomId);
        accessGuardService.requirePropertyManagementAccess(currentUser, room.getProperty().getId(), "READ");

        String cleanCode = createBedRequestDTO.bedCode().trim().toUpperCase();
        if (bedRepository.existsByRoomIdAndBedCodeIgnoreCase(roomId, cleanCode)) {
            throw new ConflictException("Bed code already exists in this room");
        }
        BedStatus requestedStatus = Optional.ofNullable(createBedRequestDTO.bedStatus()).orElse(BedStatus.VACANT);
        if (requestedStatus == BedStatus.OCCUPIED) {
            throw new ValidationException("Use the move-in workflow to mark a bed as occupied");
        }

        BedEntity savedBed = bedRepository.save(buildBedEntity(room, cleanCode, requestedStatus, createBedRequestDTO));
        syncRoomCapacityFromBeds(room);
        log.info("Bed created. bedId={}, roomId={}", savedBed.getId(), roomId);
        return bedMapper.toBedResponseDTO(savedBed, null, null);
    }

    @Override
    @Transactional
    public BedResponseDTO updateBed(String principalEmail, UUID bedId, UpdateBedRequestDTO updateBedRequestDTO) {
        User currentUser = accessGuardService.loadActiveUser(principalEmail);
        if (currentUser.getRole() != Role.PG_OWNER) {
            throw new ForbiddenException("Only PG owners can update bed details");
        }
        BedEntity bedEntity = findBedWithContext(bedId);
        accessGuardService.requirePropertyManagementAccess(currentUser, bedEntity.getProperty().getId(), "READ");

        String cleanCode = updateBedRequestDTO.bedCode().trim().toUpperCase();
        if (bedRepository.existsByRoomIdAndBedCodeIgnoreCaseAndIdNot(bedEntity.getRoom().getId(), cleanCode, bedId)) {
            throw new ConflictException("Another bed in this room already uses this code");
        }
        bedEntity.setBedCode(cleanCode);
        Optional.ofNullable(updateBedRequestDTO.monthlyRent()).ifPresent(bedEntity::setMonthlyRent);
        Optional.ofNullable(updateBedRequestDTO.defaultDeposit()).ifPresent(bedEntity::setDefaultDeposit);
        bedEntity.setAvailableFrom(updateBedRequestDTO.availableFrom());
        bedEntity.setNotes(StringUtils.trimToNull(updateBedRequestDTO.notes()));
        BedEntity savedBed = bedRepository.save(bedEntity);

        Optional<TenancyEntity> activeTenancy = tenancyRepository
                .findFirstByBedIdAndTenancyStatusIn(bedId, ACTIVE_TENANCY_STATUSES);
        return bedMapper.toBedResponseDTO(
                savedBed,
                activeTenancy.map(TenancyEntity::getId).orElse(null),
                activeTenancy.map(tenancy -> tenancy.getTenant().getFullName()).orElse(null)
        );
    }

    @Override
    @Transactional
    public BedResponseDTO updateBedStatus(
            String principalEmail,
            UUID bedId,
            UpdateBedStatusRequestDTO statusRequestDTO
    ) {
        User currentUser = accessGuardService.loadActiveUser(principalEmail);
        BedEntity bedEntity = findBedWithContext(bedId);
        accessGuardService.requirePropertyManagementAccess(currentUser, bedEntity.getProperty().getId(), "TENANTS");

        BedStatus targetStatus = statusRequestDTO.bedStatus();
        if (targetStatus == BedStatus.OCCUPIED) {
            throw new ValidationException("Beds become occupied only through a confirmed move-in");
        }
        if (tenancyRepository.existsByBedIdAndTenancyStatusIn(bedId, ACTIVE_TENANCY_STATUSES)) {
            throw new ConflictException("Complete move-out or bed transfer before changing this occupied bed's status");
        }

        bedEntity.setBedStatus(targetStatus);
        bedEntity.setAvailableFrom(statusRequestDTO.availableFrom());
        bedEntity.setReservedUntil(targetStatus == BedStatus.RESERVED ? statusRequestDTO.reservedUntil() : null);
        if (StringUtils.isNotBlank(statusRequestDTO.notes())) {
            bedEntity.setNotes(statusRequestDTO.notes().trim());
        }
        return bedMapper.toBedResponseDTO(bedRepository.save(bedEntity), null, null);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponseDTO<BedAvailabilityResponseDTO> findAvailableBeds(
            String principalEmail,
            UUID propertyId,
            BedStatus bedStatus,
            String roomType,
            BigDecimal maxRent,
            LocalDate availableBefore,
            int page,
            Integer size,
            String sortBy,
            String sortDirection
    ) {
        User currentUser = accessGuardService.loadActiveUser(principalEmail);
        List<UUID> propertyIds = accessGuardService.resolveAccessiblePropertyIds(
                currentUser, Optional.ofNullable(propertyId)
        );
        Pageable pageable = PaginationUtils.createPageable(
                page, size, sortBy, sortDirection, BED_SORT_FIELDS, "bedCode", appProperties
        );
        if (CollectionUtils.isEmpty(propertyIds)) {
            return PageResponseDTO.from(Page.empty(pageable));
        }

        Optional<BedStatus> bedStatusOptional = Optional.ofNullable(bedStatus);
        Optional<BigDecimal> maxRentOptional = Optional.ofNullable(maxRent);
        Optional<LocalDate> availableBeforeOptional = Optional.ofNullable(availableBefore);
        boolean hasRoomTypeFilter = StringUtils.isNotBlank(roomType);

        Page<BedAvailabilityResponseDTO> pageResult = bedRepository.findAvailability(
                        propertyIds,
                        bedStatusOptional.isPresent(),
                        bedStatusOptional.orElse(BedStatus.VACANT),
                        hasRoomTypeFilter,
                        hasRoomTypeFilter ? roomType.trim().toUpperCase() : "",
                        maxRentOptional.isPresent(),
                        maxRentOptional.orElse(BigDecimal.ZERO),
                        availableBeforeOptional.isPresent(),
                        availableBeforeOptional.orElse(LocalDate.now()),
                        pageable
                )
                .map(bedMapper::toBedAvailabilityResponseDTO);
        return PageResponseDTO.from(pageResult);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponseDTO<TenancyResponseDTO> getBedHistory(
            String principalEmail,
            UUID bedId,
            int page,
            Integer size,
            String sortBy,
            String sortDirection
    ) {
        User currentUser = accessGuardService.loadActiveUser(principalEmail);
        BedEntity bedEntity = findBedWithContext(bedId);
        accessGuardService.requirePropertyManagementAccess(currentUser, bedEntity.getProperty().getId(), "READ");
        Pageable pageable = PaginationUtils.createPageable(
                page, size, sortBy, sortDirection, HISTORY_SORT_FIELDS, "moveInDate", appProperties
        );
        return PageResponseDTO.from(tenancyRepository.findAllByBedId(bedId, pageable)
                .map(tenancyMapper::toTenancyResponseDTO));
    }

    private Room findPropertyScopedRoom(UUID roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found"));
        Optional.ofNullable(room.getProperty())
                .orElseThrow(() -> new ValidationException("Room is not linked to a property"));
        return room;
    }

    private BedEntity findBedWithContext(UUID bedId) {
        return bedRepository.findWithContextById(bedId)
                .orElseThrow(() -> new ResourceNotFoundException("Bed not found"));
    }

    private BedEntity buildBedEntity(
            Room room,
            String cleanCode,
            BedStatus requestedStatus,
            CreateBedRequestDTO requestDTO
    ) {
        BedEntity bed = new BedEntity();
        bed.setOrganization(room.getProperty().getOrganization());
        bed.setProperty(room.getProperty());
        bed.setRoom(room);
        bed.setBedCode(cleanCode);
        bed.setMonthlyRent(Optional.ofNullable(requestDTO.monthlyRent())
                .or(() -> Optional.ofNullable(room.getBaseMonthlyRent()))
                .orElse(BigDecimal.valueOf(room.getPrice())));
        bed.setDefaultDeposit(Optional.ofNullable(requestDTO.defaultDeposit())
                .or(() -> Optional.ofNullable(room.getDefaultDeposit()))
                .orElse(BigDecimal.ZERO));
        bed.setBedStatus(requestedStatus);
        bed.setAvailableFrom(requestDTO.availableFrom());
        bed.setNotes(StringUtils.trimToNull(requestDTO.notes()));
        return bed;
    }

    private void syncRoomCapacityFromBeds(Room room) {
        int bedTotal = (int) bedRepository.countByRoomId(room.getId());
        if (bedTotal > room.getCapacity()) {
            room.setCapacity(bedTotal);
            roomRepository.save(room);
        }
    }
}
