package com.upcraft.stay.electricity.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.upcraft.stay.billing.entity.InvoiceEntity;
import com.upcraft.stay.billing.service.InvoiceService;
import com.upcraft.stay.common.config.AppProperties;
import com.upcraft.stay.common.constants.AppConstants;
import com.upcraft.stay.common.dto.PageResponseDTO;
import com.upcraft.stay.common.enums.ElectricityBillStatus;
import com.upcraft.stay.common.enums.MeterReadingStatus;
import com.upcraft.stay.common.enums.MeterScope;
import com.upcraft.stay.common.enums.MeterStatus;
import com.upcraft.stay.common.enums.SplitMethod;
import com.upcraft.stay.common.enums.TenancyStatus;
import com.upcraft.stay.common.exception.ConflictException;
import com.upcraft.stay.common.exception.ResourceNotFoundException;
import com.upcraft.stay.common.util.PaginationUtils;
import com.upcraft.stay.domain.entity.User;
import com.upcraft.stay.electricity.dto.ConsumptionReportResponseDTO;
import com.upcraft.stay.electricity.dto.ElectricityBillResponseDTO;
import com.upcraft.stay.electricity.dto.GenerateElectricityBillsRequestDTO;
import com.upcraft.stay.electricity.dto.MeterConsumptionBreakdownResponseDTO;
import com.upcraft.stay.electricity.entity.ElectricityBillEntity;
import com.upcraft.stay.electricity.entity.MeterEntity;
import com.upcraft.stay.electricity.entity.MeterReadingEntity;
import com.upcraft.stay.electricity.mapper.ElectricityMapper;
import com.upcraft.stay.electricity.repository.ElectricityBillRepository;
import com.upcraft.stay.electricity.repository.MeterReadingRepository;
import com.upcraft.stay.electricity.repository.MeterRepository;
import com.upcraft.stay.electricity.service.ElectricityBillingService;
import com.upcraft.stay.organization.entity.OrganizationEntity;
import com.upcraft.stay.security.AccessGuardService;
import com.upcraft.stay.tenant.entity.TenancyEntity;
import com.upcraft.stay.tenant.repository.TenancyRepository;

/**
 * Electricity bill apportionment, M3 invoice integration, resident self-view, and consumption analytics.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ElectricityBillingServiceImpl implements ElectricityBillingService {

    private static final Set<String> SORT_FIELDS = Set.of("createdAt", "totalAmount", "billingYear", "billingMonth");
    private static final List<TenancyStatus> ALLOCATABLE_STATUSES = List.of(
            TenancyStatus.ACTIVE, TenancyStatus.NOTICE_PERIOD, TenancyStatus.COMPLETED
    );

    private final AccessGuardService accessGuardService;
    private final MeterRepository meterRepository;
    private final MeterReadingRepository meterReadingRepository;
    private final ElectricityBillRepository electricityBillRepository;
    private final TenancyRepository tenancyRepository;
    private final InvoiceService invoiceService;
    private final ElectricityCalculationHelper calculationHelper;
    private final ElectricityMapper electricityMapper;
    private final AppProperties appProperties;

    @Override
    @Transactional
    public List<ElectricityBillResponseDTO> generateBillsForReading(
            String principalEmail,
            UUID readingId,
            GenerateElectricityBillsRequestDTO generateRequestDTO
    ) {
        User currentUser = accessGuardService.loadActiveUser(principalEmail);
        MeterReadingEntity reading = meterReadingRepository.findWithDetailsById(readingId)
                .orElseThrow(() -> new ResourceNotFoundException("Meter reading not found"));
        accessGuardService.requirePropertyManagementAccess(currentUser, reading.getProperty().getId(), "PAYMENTS");

        validateReadingBillable(reading);
        MeterEntity meter = reading.getMeter();
        YearMonth billingPeriod = YearMonth.of(reading.getBillingYear(), reading.getBillingMonth());
        List<TenancyEntity> candidateTenancies = resolveCandidateTenancies(meter, billingPeriod);
        if (CollectionUtils.isEmpty(candidateTenancies)) {
            throw new ConflictException("No active or billable tenancies found to allocate this meter reading");
        }

        SplitMethod splitMethod = Optional.ofNullable(generateRequestDTO)
                .map(GenerateElectricityBillsRequestDTO::splitMethodOverride)
                .orElse(meter.getSplitMethod());
        boolean linkInvoice = Optional.ofNullable(generateRequestDTO)
                .map(GenerateElectricityBillsRequestDTO::linkToInvoice)
                .orElse(true);
        LocalDate dueDate = Optional.ofNullable(generateRequestDTO)
                .map(GenerateElectricityBillsRequestDTO::dueDate)
                .orElse(null);

        List<ElectricityCalculationHelper.CalculatedShare> shares = calculationHelper.computeTenantShares(
                reading, splitMethod, candidateTenancies,
                Optional.ofNullable(generateRequestDTO).map(GenerateElectricityBillsRequestDTO::allocations).orElse(null)
        );
        AtomicLong billSeq = new AtomicLong(
                electricityBillRepository.countByOrganizationId(reading.getOrganization().getId())
        );

        List<ElectricityBillEntity> billsToSave = shares.stream()
                .map(share -> createBillEntity(
                        reading, meter, splitMethod, billingPeriod, billSeq.incrementAndGet(),
                        share, linkInvoice, dueDate
                ))
                .toList();

        List<ElectricityBillEntity> savedBills = electricityBillRepository.saveAll(billsToSave);
        reading.setReadingStatus(MeterReadingStatus.BILLED);
        meterReadingRepository.save(reading);

        log.info("Generated {} electricity bills. readingId={}, totalAmount={}",
                savedBills.size(), readingId, reading.getTotalAmount());
        return savedBills.stream()
                .map(electricityMapper::toElectricityBillResponseDTO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponseDTO<ElectricityBillResponseDTO> listBills(
            String principalEmail,
            UUID propertyId,
            UUID tenantId,
            UUID meterId,
            Integer billingYear,
            Integer billingMonth,
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
                page, size, sortBy, sortDirection, SORT_FIELDS, "createdAt", appProperties
        );
        if (CollectionUtils.isEmpty(propertyIds)) {
            return PageResponseDTO.from(Page.empty(pageable));
        }

        Optional<UUID> tenantOpt = Optional.ofNullable(tenantId);
        Optional<UUID> meterOpt = Optional.ofNullable(meterId);
        Optional<Integer> yearOpt = Optional.ofNullable(billingYear);
        Optional<Integer> monthOpt = Optional.ofNullable(billingMonth);

        return PageResponseDTO.from(electricityBillRepository.findAccessibleBills(
                        propertyIds,
                        tenantOpt.isPresent(), tenantOpt.orElseGet(UUID::randomUUID),
                        meterOpt.isPresent(), meterOpt.orElseGet(UUID::randomUUID),
                        yearOpt.isPresent(), yearOpt.orElse(0),
                        monthOpt.isPresent(), monthOpt.orElse(0),
                        pageable
                ).map(electricityMapper::toElectricityBillResponseDTO)
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponseDTO<ElectricityBillResponseDTO> listMyBills(
            String principalEmail,
            int page,
            Integer size,
            String sortBy,
            String sortDirection
    ) {
        User residentUser = accessGuardService.loadActiveUser(principalEmail);
        OrganizationEntity organization = accessGuardService.resolveUserOrganization(residentUser);
        Pageable pageable = PaginationUtils.createPageable(
                page, size, sortBy, sortDirection, SORT_FIELDS, "createdAt", appProperties
        );
        Page<ElectricityBillResponseDTO> result = electricityBillRepository.findSelfBills(
                        residentUser.getId(),
                        organization.getId(),
                        residentUser.getEmail().trim().toLowerCase(Locale.ROOT),
                        pageable
                )
                .map(electricityMapper::toElectricityBillResponseDTO);
        return PageResponseDTO.from(result);
    }

    @Override
    @Transactional(readOnly = true)
    public ConsumptionReportResponseDTO getConsumptionReport(
            String principalEmail,
            UUID propertyId,
            Integer billingYear,
            Integer billingMonth
    ) {
        User currentUser = accessGuardService.loadActiveUser(principalEmail);
        List<UUID> propertyIds = accessGuardService.resolveAccessiblePropertyIds(
                currentUser, Optional.ofNullable(propertyId)
        );
        if (CollectionUtils.isEmpty(propertyIds)) {
            return new ConsumptionReportResponseDTO(
                    0L, 0L, 0L, 0L, 0L, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of()
            );
        }

        List<MeterEntity> meters = meterRepository.findAccessibleMeters(
                propertyIds, false, MeterScope.ROOM, false, MeterStatus.ACTIVE,
                false, "", PageRequest.of(0, 500)
        ).getContent();
        Optional<Integer> yearOpt = Optional.ofNullable(billingYear);
        Optional<Integer> monthOpt = Optional.ofNullable(billingMonth);

        List<MeterReadingEntity> readings = meterReadingRepository.findReportReadings(
                propertyIds, MeterReadingStatus.REJECTED,
                yearOpt.isPresent(), yearOpt.orElse(0),
                monthOpt.isPresent(), monthOpt.orElse(0)
        );

        long activeMeters = meters.stream().filter(m -> m.getMeterStatus() == MeterStatus.ACTIVE).count();
        long pendingApprovals = readings.stream()
                .filter(reading -> reading.getReadingStatus() == MeterReadingStatus.SUBMITTED).count();
        long unusualCount = readings.stream().filter(MeterReadingEntity::isUnusualUsage).count();
        BigDecimal totalUnits = readings.stream()
                .map(MeterReadingEntity::getUnitsConsumed)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalAmount = readings.stream()
                .map(MeterReadingEntity::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        BigDecimal averageUnits = readings.isEmpty()
                ? BigDecimal.ZERO
                : totalUnits.divide(BigDecimal.valueOf(readings.size()), 2, RoundingMode.HALF_UP);

        Map<UUID, List<MeterReadingEntity>> readingsByMeter = readings.stream()
                .collect(Collectors.groupingBy(reading -> reading.getMeter().getId()));
        List<MeterConsumptionBreakdownResponseDTO> breakdowns = meters.stream()
                .map(meter -> buildBreakdown(meter, readingsByMeter.getOrDefault(meter.getId(), List.of())))
                .toList();

        return new ConsumptionReportResponseDTO(
                meters.size(), activeMeters, readings.size(), pendingApprovals, unusualCount,
                totalUnits, totalAmount, averageUnits, breakdowns
        );
    }

    private void validateReadingBillable(MeterReadingEntity reading) {
        if (reading.getReadingStatus() == MeterReadingStatus.SUBMITTED) {
            throw new ConflictException("Approve meter reading before generating electricity bills");
        }
        if (reading.getReadingStatus() == MeterReadingStatus.REJECTED) {
            throw new ConflictException("Rejected readings cannot generate electricity bills");
        }
        if (reading.getReadingStatus() == MeterReadingStatus.BILLED
                || electricityBillRepository.existsByReadingIdAndBillStatusNot(
                        reading.getId(), ElectricityBillStatus.CANCELLED)) {
            throw new ConflictException("Electricity bills have already been generated for this reading");
        }
    }

    private List<TenancyEntity> resolveCandidateTenancies(MeterEntity meter, YearMonth billingPeriod) {
        List<TenancyEntity> propertyTenancies = tenancyRepository.findBillableTenanciesForPeriod(
                List.of(meter.getProperty().getId()), ALLOCATABLE_STATUSES,
                billingPeriod.atDay(1), billingPeriod.atEndOfMonth()
        );
        return switch (meter.getMeterScope()) {
            case BED -> propertyTenancies.stream()
                    .filter(tenancy -> Optional.ofNullable(meter.getBed())
                            .map(bed -> bed.getId().equals(tenancy.getBed().getId()))
                            .orElse(false))
                    .toList();
            case ROOM -> propertyTenancies.stream()
                    .filter(tenancy -> Optional.ofNullable(meter.getRoom())
                            .map(room -> room.getId().equals(tenancy.getRoom().getId()))
                            .orElse(false))
                    .toList();
            case SHARED, MAIN -> propertyTenancies;
        };
    }

    private ElectricityBillEntity createBillEntity(
            MeterReadingEntity reading,
            MeterEntity meter,
            SplitMethod splitMethod,
            YearMonth period,
            long sequence,
            ElectricityCalculationHelper.CalculatedShare share,
            boolean linkInvoice,
            LocalDate dueDate
    ) {
        TenancyEntity tenancy = share.tenancy();
        BigDecimal fixedAndExtra = share.fixedChargeShare().add(share.additionalChargeShare())
                .setScale(2, RoundingMode.HALF_UP);
        InvoiceEntity linkedInvoice = linkInvoice
                ? invoiceService.createElectricityInvoice(
                        tenancy, period, dueDate, meter.getMeterNumber(),
                        share.unitsAllocated(), reading.getRatePerUnitSnapshot(),
                        share.energyCharge(), fixedAndExtra, share.taxAmountShare(), share.creditShare()
                )
                : null;

        ElectricityBillEntity bill = new ElectricityBillEntity();
        bill.setOrganization(reading.getOrganization());
        bill.setProperty(reading.getProperty());
        bill.setMeter(meter);
        bill.setReading(reading);
        bill.setTenancy(tenancy);
        bill.setTenant(tenancy.getTenant());
        bill.setRoom(tenancy.getRoom());
        bill.setBed(tenancy.getBed());
        bill.setInvoice(linkedInvoice);
        bill.setBillNumber(calculationHelper.nextBillNumber(period, sequence));
        bill.setBillingYear(period.getYear());
        bill.setBillingMonth(period.getMonthValue());
        bill.setSplitMethodSnapshot(splitMethod);
        bill.setSharePercentage(share.sharePercentage());
        bill.setUnitsAllocated(share.unitsAllocated());
        bill.setRatePerUnitSnapshot(reading.getRatePerUnitSnapshot());
        bill.setEnergyCharge(share.energyCharge());
        bill.setFixedChargeShare(share.fixedChargeShare());
        bill.setAdditionalChargeShare(share.additionalChargeShare());
        bill.setTaxAmountShare(share.taxAmountShare());
        bill.setCreditShare(share.creditShare());
        bill.setTotalAmount(share.totalAmount());
        bill.setBillStatus(Optional.ofNullable(linkedInvoice).isPresent()
                ? ElectricityBillStatus.INVOICED
                : ElectricityBillStatus.GENERATED);
        return bill;
    }

    private MeterConsumptionBreakdownResponseDTO buildBreakdown(MeterEntity meter, List<MeterReadingEntity> meterReadings) {
        BigDecimal totalUnits = meterReadings.stream()
                .map(MeterReadingEntity::getUnitsConsumed)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalAmount = meterReadings.stream()
                .map(MeterReadingEntity::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        long unusualCount = meterReadings.stream().filter(MeterReadingEntity::isUnusualUsage).count();
        String location = Optional.ofNullable(meter.getBed())
                .map(bed -> String.format("Room %s / Bed %s", bed.getRoom().getNumber(), bed.getBedCode()))
                .or(() -> Optional.ofNullable(meter.getRoom()).map(room -> "Room " + room.getNumber()))
                .orElse("Shared / Property");
        return new MeterConsumptionBreakdownResponseDTO(
                meter.getId(), meter.getMeterNumber(), meter.getMeterLabel(), meter.getMeterScope(),
                location, meterReadings.size(), totalUnits, totalAmount, unusualCount
        );
    }
}

