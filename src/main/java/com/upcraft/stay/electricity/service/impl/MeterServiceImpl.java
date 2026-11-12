package com.upcraft.stay.electricity.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
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
import com.upcraft.stay.common.enums.MeterScope;
import com.upcraft.stay.common.enums.MeterStatus;
import com.upcraft.stay.common.exception.ConflictException;
import com.upcraft.stay.common.exception.ResourceNotFoundException;
import com.upcraft.stay.common.exception.ValidationException;
import com.upcraft.stay.common.util.PaginationUtils;
import com.upcraft.stay.domain.entity.Room;
import com.upcraft.stay.domain.entity.User;
import com.upcraft.stay.electricity.dto.CreateMeterRequestDTO;
import com.upcraft.stay.electricity.dto.MeterResponseDTO;
import com.upcraft.stay.electricity.dto.UpdateMeterRequestDTO;
import com.upcraft.stay.electricity.entity.MeterEntity;
import com.upcraft.stay.electricity.mapper.ElectricityMapper;
import com.upcraft.stay.electricity.repository.MeterRepository;
import com.upcraft.stay.electricity.service.MeterService;
import com.upcraft.stay.property.entity.PropertyEntity;
import com.upcraft.stay.repository.RoomRepository;
import com.upcraft.stay.room.entity.BedEntity;
import com.upcraft.stay.room.repository.BedRepository;
import com.upcraft.stay.security.AccessGuardService;

/**
 * Property-scoped electricity meter configuration and lifecycle management service.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MeterServiceImpl implements MeterService {

    private static final Set<String> SORT_FIELDS = Set.of("meterNumber", "createdAt", "unitRate", "lastReadingDate");

    private final AccessGuardService accessGuardService;
    private final MeterRepository meterRepository;
    private final RoomRepository roomRepository;
    private final BedRepository bedRepository;
    private final ElectricityMapper electricityMapper;
    private final AppProperties appProperties;

    @Override
    @Transactional
    public MeterResponseDTO createMeter(String principalEmail, CreateMeterRequestDTO createRequestDTO) {
        User currentUser = accessGuardService.loadActiveUser(principalEmail);
        PropertyEntity property = accessGuardService.requirePropertyManagementAccess(
                currentUser, createRequestDTO.propertyId(), "METERS"
        );
        String cleanNumber = createRequestDTO.meterNumber().trim();
        if (meterRepository.existsByPropertyIdAndMeterNumberIgnoreCase(property.getId(), cleanNumber)) {
            throw new ConflictException("A meter with this identifier already exists on this property");
        }

        BedEntity bed = resolveTargetBed(createRequestDTO, property.getId());
        Room room = resolveTargetRoom(createRequestDTO, bed, property.getId());

        MeterEntity meter = new MeterEntity();
        meter.setOrganization(property.getOrganization());
        meter.setProperty(property);
        meter.setRoom(room);
        meter.setBed(bed);
        meter.setMeterNumber(cleanNumber);
        meter.setMeterLabel(StringUtils.trimToNull(createRequestDTO.meterLabel()));
        meter.setMeterScope(createRequestDTO.meterScope());
        meter.setUnitRate(Optional.ofNullable(createRequestDTO.unitRate())
                .orElse(property.getDefaultElectricityRate())
                .setScale(4, RoundingMode.HALF_UP));
        meter.setFixedCharge(Optional.ofNullable(createRequestDTO.fixedCharge())
                .orElse(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP));
        meter.setSplitMethod(createRequestDTO.splitMethod());
        BigDecimal initialReading = Optional.ofNullable(createRequestDTO.initialReading())
                .orElse(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);
        meter.setInitialReading(initialReading);
        meter.setLastReadingValue(initialReading);
        meter.setNotes(StringUtils.trimToNull(createRequestDTO.notes()));

        MeterEntity saved = meterRepository.save(meter);
        log.info("Electricity meter created. meterId={}, propertyId={}, scope={}",
                saved.getId(), property.getId(), saved.getMeterScope());
        return electricityMapper.toMeterResponseDTO(saved);
    }

    @Override
    @Transactional
    public MeterResponseDTO updateMeter(
            String principalEmail,
            UUID meterId,
            UpdateMeterRequestDTO updateRequestDTO
    ) {
        User currentUser = accessGuardService.loadActiveUser(principalEmail);
        MeterEntity meter = meterRepository.findWithDetailsById(meterId)
                .orElseThrow(() -> new ResourceNotFoundException("Electricity meter not found"));
        accessGuardService.requirePropertyManagementAccess(currentUser, meter.getProperty().getId(), "METERS");

        meter.setMeterLabel(StringUtils.trimToNull(updateRequestDTO.meterLabel()));
        meter.setUnitRate(updateRequestDTO.unitRate().setScale(4, RoundingMode.HALF_UP));
        meter.setFixedCharge(updateRequestDTO.fixedCharge().setScale(2, RoundingMode.HALF_UP));
        meter.setSplitMethod(updateRequestDTO.splitMethod());
        meter.setMeterStatus(updateRequestDTO.meterStatus());
        meter.setNotes(StringUtils.trimToNull(updateRequestDTO.notes()));

        MeterEntity saved = meterRepository.save(meter);
        log.info("Electricity meter updated without altering historical readings. meterId={}", saved.getId());
        return electricityMapper.toMeterResponseDTO(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponseDTO<MeterResponseDTO> listMeters(
            String principalEmail,
            UUID propertyId,
            MeterScope meterScope,
            MeterStatus meterStatus,
            String search,
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
                page, size, sortBy, sortDirection, SORT_FIELDS, "meterNumber", appProperties
        );
        if (CollectionUtils.isEmpty(propertyIds)) {
            return PageResponseDTO.from(Page.empty(pageable));
        }

        Optional<MeterScope> scopeOpt = Optional.ofNullable(meterScope);
        Optional<MeterStatus> statusOpt = Optional.ofNullable(meterStatus);
        String trimmed = StringUtils.trimToEmpty(search);

        Page<MeterResponseDTO> result = meterRepository.findAccessibleMeters(
                        propertyIds,
                        scopeOpt.isPresent(), scopeOpt.orElse(MeterScope.ROOM),
                        statusOpt.isPresent(), statusOpt.orElse(MeterStatus.ACTIVE),
                        StringUtils.isNotBlank(trimmed), "%" + trimmed.toLowerCase(Locale.ROOT) + "%",
                        pageable
                )
                .map(electricityMapper::toMeterResponseDTO);
        return PageResponseDTO.from(result);
    }

    private BedEntity resolveTargetBed(CreateMeterRequestDTO requestDTO, UUID propertyId) {
        if (requestDTO.meterScope() == MeterScope.BED && Optional.ofNullable(requestDTO.bedId()).isEmpty()) {
            throw new ValidationException("Bed ID is required for BED scope meters");
        }
        return Optional.ofNullable(requestDTO.bedId())
                .map(bedId -> bedRepository.findById(bedId)
                        .filter(bed -> bed.getProperty().getId().equals(propertyId))
                        .orElseThrow(() -> new ValidationException("Selected bed does not belong to target property")))
                .orElse(null);
    }

    private Room resolveTargetRoom(CreateMeterRequestDTO requestDTO, BedEntity bed, UUID propertyId) {
        if (Optional.ofNullable(bed).isPresent()) {
            return bed.getRoom();
        }
        if (requestDTO.meterScope() == MeterScope.ROOM && Optional.ofNullable(requestDTO.roomId()).isEmpty()) {
            throw new ValidationException("Room ID is required for ROOM scope meters");
        }
        return Optional.ofNullable(requestDTO.roomId())
                .map(roomId -> roomRepository.findById(roomId)
                        .filter(room -> room.getProperty().getId().equals(propertyId))
                        .orElseThrow(() -> new ValidationException("Selected room does not belong to target property")))
                .orElse(null);
    }
}

