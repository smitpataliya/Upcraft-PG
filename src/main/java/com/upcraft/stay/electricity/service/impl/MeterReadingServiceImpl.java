package com.upcraft.stay.electricity.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
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
import com.upcraft.stay.common.enums.MeterReadingStatus;
import com.upcraft.stay.common.enums.MeterStatus;
import com.upcraft.stay.common.exception.ConflictException;
import com.upcraft.stay.common.exception.ResourceNotFoundException;
import com.upcraft.stay.common.exception.ValidationException;
import com.upcraft.stay.common.util.PaginationUtils;
import com.upcraft.stay.domain.entity.User;
import com.upcraft.stay.electricity.dto.ApproveReadingRequestDTO;
import com.upcraft.stay.electricity.dto.MeterReadingResponseDTO;
import com.upcraft.stay.electricity.dto.RecordMeterReadingRequestDTO;
import com.upcraft.stay.electricity.dto.RejectReadingRequestDTO;
import com.upcraft.stay.electricity.entity.MeterEntity;
import com.upcraft.stay.electricity.entity.MeterReadingEntity;
import com.upcraft.stay.electricity.mapper.ElectricityMapper;
import com.upcraft.stay.electricity.repository.MeterReadingRepository;
import com.upcraft.stay.electricity.repository.MeterRepository;
import com.upcraft.stay.electricity.service.MeterReadingService;
import com.upcraft.stay.security.AccessGuardService;
import com.upcraft.stay.storage.entity.StoredFileEntity;
import com.upcraft.stay.storage.repository.StoredFileRepository;

/**
 * Electricity reading capture, tariff snapshotting, anomaly detection, reset controls, and approval workflow.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MeterReadingServiceImpl implements MeterReadingService {

    private static final Set<String> SORT_FIELDS = Set.of("readingDate", "unitsConsumed", "totalAmount", "createdAt");

    private final AccessGuardService accessGuardService;
    private final MeterRepository meterRepository;
    private final MeterReadingRepository meterReadingRepository;
    private final StoredFileRepository storedFileRepository;
    private final ElectricityCalculationHelper calculationHelper;
    private final ElectricityMapper electricityMapper;
    private final AppProperties appProperties;

    @Override
    @Transactional
    public MeterReadingResponseDTO recordReading(
            String principalEmail,
            UUID meterId,
            RecordMeterReadingRequestDTO recordRequestDTO
    ) {
        User currentUser = accessGuardService.loadActiveUser(principalEmail);
        MeterEntity meter = meterRepository.findWithDetailsById(meterId)
                .orElseThrow(() -> new ResourceNotFoundException("Electricity meter not found"));
        accessGuardService.requirePropertyManagementAccess(currentUser, meter.getProperty().getId(), "METERS");

        validateMeterWritableAndUniquePeriod(meter, recordRequestDTO);
        BigDecimal previousValue = Optional.ofNullable(recordRequestDTO.previousValue())
                .orElseGet(() -> Optional.ofNullable(meter.getLastReadingValue()).orElse(meter.getInitialReading()))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal currentValue = recordRequestDTO.currentValue().setScale(2, RoundingMode.HALF_UP);

        boolean resetApplied = validateResetOrCorrection(currentUser, meter, previousValue, currentValue, recordRequestDTO);
        BigDecimal unitsConsumed = currentValue.compareTo(previousValue) >= 0
                ? currentValue.subtract(previousValue).setScale(2, RoundingMode.HALF_UP)
                : currentValue;

        MeterReadingEntity reading = buildReadingSnapshot(
                currentUser, meter, previousValue, currentValue, unitsConsumed, resetApplied, recordRequestDTO
        );
        attachPriorAnomalyAndPhoto(reading, meter, unitsConsumed, recordRequestDTO);
        applyOptionalAutoApproval(currentUser, meter, reading, recordRequestDTO);

        MeterReadingEntity saved = meterReadingRepository.save(reading);
        meter.setLastReadingValue(currentValue);
        meter.setLastReadingDate(recordRequestDTO.readingDate());
        meterRepository.save(meter);

        log.info("Meter reading recorded. readingId={}, meterId={}, units={}, unusual={}",
                saved.getId(), meterId, unitsConsumed, saved.isUnusualUsage());
        return electricityMapper.toMeterReadingResponseDTO(
                meterReadingRepository.findWithDetailsById(saved.getId()).orElse(saved)
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponseDTO<MeterReadingResponseDTO> listReadingsForMeter(
            String principalEmail,
            UUID meterId,
            int page,
            Integer size
    ) {
        User currentUser = accessGuardService.loadActiveUser(principalEmail);
        MeterEntity meter = meterRepository.findWithDetailsById(meterId)
                .orElseThrow(() -> new ResourceNotFoundException("Electricity meter not found"));
        accessGuardService.requirePropertyManagementAccess(currentUser, meter.getProperty().getId(), "READ");

        Pageable pageable = PaginationUtils.createPageable(
                page, size, "readingDate", "DESC", SORT_FIELDS, "readingDate", appProperties
        );
        return PageResponseDTO.from(
                meterReadingRepository.findAllByMeterIdOrderByReadingDateDescCreatedAtDesc(meterId, pageable)
                        .map(electricityMapper::toMeterReadingResponseDTO)
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponseDTO<MeterReadingResponseDTO> listReadings(
            String principalEmail,
            UUID propertyId,
            UUID meterId,
            MeterReadingStatus readingStatus,
            Integer billingYear,
            Integer billingMonth,
            Boolean unusualOnly,
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
                page, size, sortBy, sortDirection, SORT_FIELDS, "readingDate", appProperties
        );
        if (CollectionUtils.isEmpty(propertyIds)) {
            return PageResponseDTO.from(Page.empty(pageable));
        }

        Optional<UUID> meterOpt = Optional.ofNullable(meterId);
        Optional<MeterReadingStatus> statusOpt = Optional.ofNullable(readingStatus);
        Optional<Integer> yearOpt = Optional.ofNullable(billingYear);
        Optional<Integer> monthOpt = Optional.ofNullable(billingMonth);

        return PageResponseDTO.from(meterReadingRepository.findAccessibleReadings(
                        propertyIds,
                        meterOpt.isPresent(), meterOpt.orElseGet(UUID::randomUUID),
                        statusOpt.isPresent(), statusOpt.orElse(MeterReadingStatus.SUBMITTED),
                        yearOpt.isPresent(), yearOpt.orElse(0),
                        monthOpt.isPresent(), monthOpt.orElse(0),
                        Boolean.TRUE.equals(unusualOnly),
                        pageable
                ).map(electricityMapper::toMeterReadingResponseDTO)
        );
    }

    @Override
    @Transactional
    public MeterReadingResponseDTO approveReading(
            String principalEmail,
            UUID readingId,
            ApproveReadingRequestDTO approveRequestDTO
    ) {
        User currentUser = accessGuardService.loadActiveUser(principalEmail);
        MeterReadingEntity reading = meterReadingRepository.findWithDetailsById(readingId)
                .orElseThrow(() -> new ResourceNotFoundException("Meter reading not found"));
        accessGuardService.requirePropertyManagementAccess(currentUser, reading.getProperty().getId(), "PAYMENTS");

        if (reading.getReadingStatus() != MeterReadingStatus.SUBMITTED) {
            throw new ConflictException("Only submitted readings can be approved");
        }
        reading.setReadingStatus(MeterReadingStatus.APPROVED);
        reading.setApprovedByUser(currentUser);
        reading.setApprovedAt(Instant.now());
        Optional.ofNullable(approveRequestDTO)
                .map(ApproveReadingRequestDTO::notes)
                .filter(StringUtils::isNotBlank)
                .ifPresent(extraNote -> reading.setNotes(extraNote.trim()));

        MeterReadingEntity saved = meterReadingRepository.save(reading);
        log.info("Meter reading approved. readingId={}, approvedBy={}", saved.getId(), currentUser.getId());
        return electricityMapper.toMeterReadingResponseDTO(saved);
    }

    @Override
    @Transactional
    public MeterReadingResponseDTO rejectReading(
            String principalEmail,
            UUID readingId,
            RejectReadingRequestDTO rejectRequestDTO
    ) {
        User currentUser = accessGuardService.loadActiveUser(principalEmail);
        MeterReadingEntity reading = meterReadingRepository.findWithDetailsById(readingId)
                .orElseThrow(() -> new ResourceNotFoundException("Meter reading not found"));
        accessGuardService.requirePropertyManagementAccess(currentUser, reading.getProperty().getId(), "METERS");

        if (reading.getReadingStatus() == MeterReadingStatus.BILLED) {
            throw new ConflictException("Billed readings cannot be rejected");
        }
        reading.setReadingStatus(MeterReadingStatus.REJECTED);
        reading.setRejectionReason(rejectRequestDTO.reason().trim());
        reading.setApprovedByUser(currentUser);
        reading.setApprovedAt(Instant.now());

        MeterReadingEntity saved = meterReadingRepository.save(reading);
        log.info("Meter reading rejected. readingId={}", saved.getId());
        return electricityMapper.toMeterReadingResponseDTO(saved);
    }

    private void validateMeterWritableAndUniquePeriod(MeterEntity meter, RecordMeterReadingRequestDTO requestDTO) {
        if (meter.getMeterStatus() == MeterStatus.INACTIVE) {
            throw new ConflictException("Cannot record reading on an inactive meter");
        }
        if (Optional.ofNullable(requestDTO.correctionOfReadingId()).isEmpty()
                && meterReadingRepository
                        .existsByMeterIdAndBillingYearAndBillingMonthAndReadingStatusNotAndCorrectionOfReadingIsNull(
                                meter.getId(), requestDTO.billingYear(), requestDTO.billingMonth(),
                                MeterReadingStatus.REJECTED)) {
            throw new ConflictException("A reading already exists for this meter and billing period");
        }
    }

    private boolean validateResetOrCorrection(
            User currentUser,
            MeterEntity meter,
            BigDecimal previousValue,
            BigDecimal currentValue,
            RecordMeterReadingRequestDTO requestDTO
    ) {
        boolean isLowerThanPrevious = currentValue.compareTo(previousValue) < 0;
        boolean hasCorrectionRef = Optional.ofNullable(requestDTO.correctionOfReadingId()).isPresent();
        if (!isLowerThanPrevious && !hasCorrectionRef) {
            return false;
        }
        if (isLowerThanPrevious && !Boolean.TRUE.equals(requestDTO.allowResetOrCorrection())) {
            throw new ValidationException(
                    "Current reading cannot be lower than previous reading unless authorized meter reset/correction is enabled"
            );
        }
        if (StringUtils.isBlank(requestDTO.correctionReason())) {
            throw new ValidationException("Correction or meter reset requires a mandatory audit reason");
        }
        accessGuardService.requirePropertyManagementAccess(currentUser, meter.getProperty().getId(), "PAYMENTS");
        return isLowerThanPrevious;
    }

    private MeterReadingEntity buildReadingSnapshot(
            User recorder,
            MeterEntity meter,
            BigDecimal previousValue,
            BigDecimal currentValue,
            BigDecimal unitsConsumed,
            boolean resetApplied,
            RecordMeterReadingRequestDTO requestDTO
    ) {
        BigDecimal rateSnapshot = meter.getUnitRate().setScale(4, RoundingMode.HALF_UP);
        BigDecimal fixedSnapshot = meter.getFixedCharge().setScale(2, RoundingMode.HALF_UP);
        BigDecimal additional = Optional.ofNullable(requestDTO.additionalCharge()).orElse(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal tax = Optional.ofNullable(requestDTO.taxAmount()).orElse(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal credit = Optional.ofNullable(requestDTO.creditAmount()).orElse(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal energyCharge = unitsConsumed.multiply(rateSnapshot).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalAmount = energyCharge.add(fixedSnapshot).add(additional).add(tax).subtract(credit)
                .max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

        MeterReadingEntity entity = new MeterReadingEntity();
        entity.setOrganization(meter.getOrganization());
        entity.setProperty(meter.getProperty());
        entity.setMeter(meter);
        entity.setBillingYear(requestDTO.billingYear());
        entity.setBillingMonth(requestDTO.billingMonth());
        entity.setReadingDate(requestDTO.readingDate());
        entity.setPreviousValue(previousValue);
        entity.setCurrentValue(currentValue);
        entity.setUnitsConsumed(unitsConsumed);
        entity.setRatePerUnitSnapshot(rateSnapshot);
        entity.setFixedChargeSnapshot(fixedSnapshot);
        entity.setEnergyCharge(energyCharge);
        entity.setAdditionalCharge(additional);
        entity.setTaxAmount(tax);
        entity.setCreditAmount(credit);
        entity.setTotalAmount(totalAmount);
        entity.setMeterResetApplied(resetApplied);
        entity.setCorrectionReason(StringUtils.trimToNull(requestDTO.correctionReason()));
        entity.setRecordedByUser(recorder);
        entity.setNotes(StringUtils.trimToNull(requestDTO.notes()));
        Optional.ofNullable(requestDTO.correctionOfReadingId())
                .map(refId -> meterReadingRepository.findById(refId)
                        .orElseThrow(() -> new ResourceNotFoundException("Target reading for correction not found")))
                .ifPresent(entity::setCorrectionOfReading);
        return entity;
    }

    private void attachPriorAnomalyAndPhoto(
            MeterReadingEntity reading,
            MeterEntity meter,
            BigDecimal unitsConsumed,
            RecordMeterReadingRequestDTO requestDTO
    ) {
        List<MeterReadingEntity> priorReadings = meterReadingRepository
                .findTop6ByMeterIdAndReadingStatusNotOrderByReadingDateDescCreatedAtDesc(
                        meter.getId(), MeterReadingStatus.REJECTED
                );
        calculationHelper.detectUnusualUsage(priorReadings, unitsConsumed, appProperties)
                .ifPresent(reason -> {
                    reading.setUnusualUsage(true);
                    reading.setUnusualUsageReason(StringUtils.left(reason, 255));
                });

        Optional.ofNullable(requestDTO.photoFileId())
                .map(fileId -> storedFileRepository.findWithDetailsById(fileId)
                        .filter(file -> file.getOrganization().getId().equals(meter.getOrganization().getId()))
                        .orElseThrow(() -> new ValidationException("Photo attachment not found in organization")))
                .ifPresent(reading::setPhotoFile);
    }

    private void applyOptionalAutoApproval(
            User currentUser,
            MeterEntity meter,
            MeterReadingEntity reading,
            RecordMeterReadingRequestDTO requestDTO
    ) {
        if (!Boolean.TRUE.equals(requestDTO.autoApprove())) {
            reading.setReadingStatus(MeterReadingStatus.SUBMITTED);
            return;
        }
        accessGuardService.requirePropertyManagementAccess(currentUser, meter.getProperty().getId(), "PAYMENTS");
        reading.setReadingStatus(MeterReadingStatus.APPROVED);
        reading.setApprovedByUser(currentUser);
        reading.setApprovedAt(Instant.now());
    }
}

