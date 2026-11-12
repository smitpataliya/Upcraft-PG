package com.upcraft.stay.room.mapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.upcraft.stay.common.constants.AppConstants;
import com.upcraft.stay.common.enums.BedStatus;
import com.upcraft.stay.common.enums.RoomStatus;
import com.upcraft.stay.common.enums.TenancyStatus;
import com.upcraft.stay.domain.entity.Room;
import com.upcraft.stay.property.entity.PropertyEntity;
import com.upcraft.stay.room.dto.BedResponseDTO;
import com.upcraft.stay.room.dto.CreatePropertyRoomRequestDTO;
import com.upcraft.stay.room.dto.PropertyRoomResponseDTO;
import com.upcraft.stay.room.dto.UpdatePropertyRoomRequestDTO;
import com.upcraft.stay.room.entity.BedEntity;
import com.upcraft.stay.room.entity.FloorEntity;
import com.upcraft.stay.tenant.entity.TenancyEntity;
import com.upcraft.stay.tenant.repository.TenancyRepository;

/**
 * Shared assembler mapping rooms, beds, and live tenancy occupant metadata cleanly.
 */
@Component
@RequiredArgsConstructor
public class PropertyRoomAssembler {

    private static final List<TenancyStatus> LIVE_OCCUPANCY_STATUSES = List.of(
            TenancyStatus.ACTIVE,
            TenancyStatus.NOTICE_PERIOD
    );

    private final BedMapper bedMapper;
    private final TenancyRepository tenancyRepository;

    /**
     * Loads active/notice-period tenancies indexed by bed UUID in a single query.
     */
    public Map<UUID, TenancyEntity> loadActiveTenancyByBed(List<UUID> roomIds) {
        return tenancyRepository.findAllByRoomIdInAndTenancyStatusIn(roomIds, LIVE_OCCUPANCY_STATUSES)
                .stream()
                .collect(Collectors.toMap(
                        tenancy -> tenancy.getBed().getId(),
                        Function.identity(),
                        (first, second) -> first
                ));
    }

    /**
     * Converts a bed entity to {@link BedResponseDTO} using a preloaded tenancy lookup map.
     */
    public BedResponseDTO toBedDTO(BedEntity bedEntity, Map<UUID, TenancyEntity> activeTenancyByBed) {
        Optional<TenancyEntity> activeTenancy = Optional.ofNullable(activeTenancyByBed.get(bedEntity.getId()));
        return bedMapper.toBedResponseDTO(
                bedEntity,
                activeTenancy.map(TenancyEntity::getId).orElse(null),
                activeTenancy.map(tenancy -> tenancy.getTenant().getFullName()).orElse(null)
        );
    }

    /**
     * Converts a room and its beds into {@link PropertyRoomResponseDTO}.
     */
    public PropertyRoomResponseDTO toRoomResponse(
            Room room,
            List<BedEntity> beds,
            Map<UUID, TenancyEntity> activeTenancyByBed
    ) {
        List<BedResponseDTO> bedDTOs = beds.stream()
                .map(bedEntity -> toBedDTO(bedEntity, activeTenancyByBed))
                .toList();

        long vacantCount = beds.stream().filter(bed -> bed.getBedStatus() == BedStatus.VACANT).count();
        long occupiedCount = beds.stream().filter(bed -> bed.getBedStatus() == BedStatus.OCCUPIED).count();
        long reservedCount = beds.stream().filter(bed -> bed.getBedStatus() == BedStatus.RESERVED).count();
        long maintenanceCount = beds.stream().filter(bed -> bed.getBedStatus() == BedStatus.MAINTENANCE).count();

        Optional<FloorEntity> floorOptional = Optional.ofNullable(room.getFloorEntity());
        return new PropertyRoomResponseDTO(
                room.getId(),
                Optional.ofNullable(room.getProperty()).map(PropertyEntity::getId).orElse(null),
                floorOptional.map(FloorEntity::getId).orElse(null),
                floorOptional.map(FloorEntity::getName).orElse(null),
                room.getFloor(),
                room.getNumber(),
                room.getRoomType(),
                room.getSharing(),
                room.getCapacity(),
                room.getOccupied(),
                Optional.ofNullable(room.getBaseMonthlyRent()).orElse(BigDecimal.valueOf(room.getPrice())),
                Optional.ofNullable(room.getDefaultDeposit()).orElse(BigDecimal.ZERO),
                room.getRoomStatus(),
                List.copyOf(room.getAmenities()),
                room.getNotes(),
                beds.size(),
                vacantCount,
                occupiedCount,
                reservedCount,
                maintenanceCount,
                bedDTOs,
                room.getCreatedAt()
        );
    }

    /**
     * Builds a new {@link Room} entity from request values and property/floor context.
     */
    public Room buildNewRoom(
            PropertyEntity propertyEntity,
            Optional<FloorEntity> floorOptional,
            CreatePropertyRoomRequestDTO requestDTO,
            String cleanNumber
    ) {
        int resolvedFloorNum = floorOptional.map(FloorEntity::getFloorNumber)
                .or(() -> Optional.ofNullable(requestDTO.floorNumber()))
                .orElse(0);
        BigDecimal deposit = Optional.ofNullable(requestDTO.defaultDeposit()).orElse(BigDecimal.ZERO);
        List<String> amenities = Optional.ofNullable(requestDTO.amenities())
                .map(ArrayList::new)
                .orElseGet(ArrayList::new);

        return Room.builder()
                .property(propertyEntity)
                .floorEntity(floorOptional.orElse(null))
                .number(cleanNumber)
                .floor(resolvedFloorNum)
                .roomType(requestDTO.roomType().trim().toUpperCase())
                .sharing(requestDTO.sharing().trim())
                .capacity(requestDTO.capacity())
                .occupied(0)
                .price(requestDTO.baseMonthlyRent().doubleValue())
                .baseMonthlyRent(requestDTO.baseMonthlyRent())
                .defaultDeposit(deposit)
                .amenities(amenities)
                .notes(StringUtils.trimToNull(requestDTO.notes()))
                .roomStatus(RoomStatus.ACTIVE)
                .recordStatus(AppConstants.STATUS_ACTIVE)
                .build();
    }

    /**
     * Applies updated attributes onto an existing {@link Room} entity.
     */
    public void applyRoomUpdates(
            Room room,
            Optional<FloorEntity> floorOptional,
            UpdatePropertyRoomRequestDTO requestDTO,
            String cleanNumber
    ) {
        int resolvedFloorNum = floorOptional.map(FloorEntity::getFloorNumber)
                .or(() -> Optional.ofNullable(requestDTO.floorNumber()))
                .orElse(room.getFloor());
        room.setFloorEntity(floorOptional.orElse(null));
        room.setFloor(resolvedFloorNum);
        room.setNumber(cleanNumber);
        room.setRoomType(requestDTO.roomType().trim().toUpperCase());
        room.setSharing(requestDTO.sharing().trim());
        room.setCapacity(requestDTO.capacity());
        room.setPrice(requestDTO.baseMonthlyRent().doubleValue());
        room.setBaseMonthlyRent(requestDTO.baseMonthlyRent());
        room.setDefaultDeposit(Optional.ofNullable(requestDTO.defaultDeposit()).orElse(BigDecimal.ZERO));
        Optional.ofNullable(requestDTO.roomStatus()).ifPresent(room::setRoomStatus);
        Optional.ofNullable(requestDTO.amenities()).ifPresent(items -> room.setAmenities(new ArrayList<>(items)));
        room.setNotes(StringUtils.trimToNull(requestDTO.notes()));
    }
}

