package com.upcraft.stay.reporting.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.upcraft.stay.billing.entity.InvoiceEntity;
import com.upcraft.stay.billing.entity.PaymentEntity;
import com.upcraft.stay.billing.repository.DepositTransactionRepository;
import com.upcraft.stay.billing.repository.InvoiceRepository;
import com.upcraft.stay.billing.repository.PaymentRepository;
import com.upcraft.stay.common.config.AppProperties;
import com.upcraft.stay.common.config.CacheConfig;
import com.upcraft.stay.common.enums.BedStatus;
import com.upcraft.stay.common.enums.DepositTransactionType;
import com.upcraft.stay.common.enums.ElectricityBillStatus;
import com.upcraft.stay.common.enums.InvoiceStatus;
import com.upcraft.stay.common.enums.InvoiceType;
import com.upcraft.stay.common.enums.MeterReadingStatus;
import com.upcraft.stay.common.enums.TenancyStatus;
import com.upcraft.stay.domain.entity.Complaint;
import com.upcraft.stay.domain.entity.User;
import com.upcraft.stay.domain.enums.ComplaintStatus;
import com.upcraft.stay.electricity.repository.ElectricityBillRepository;
import com.upcraft.stay.electricity.repository.MeterReadingRepository;
import com.upcraft.stay.organization.entity.OrganizationEntity;
import com.upcraft.stay.reporting.dto.StaffDashboardResponseDTO;
import com.upcraft.stay.reporting.dto.TenantDashboardResponseDTO;
import com.upcraft.stay.reporting.service.DashboardV1Service;
import com.upcraft.stay.repository.ComplaintRepository;
import com.upcraft.stay.repository.NotificationRepository;
import com.upcraft.stay.repository.RoomRepository;
import com.upcraft.stay.room.repository.BedRepository;
import com.upcraft.stay.security.AccessGuardService;
import com.upcraft.stay.tenant.entity.TenancyEntity;
import com.upcraft.stay.tenant.repository.TenancyRepository;

/**
 * Computes real DB KPIs, chart series, and operational action queues for Owner, Manager, and Tenant dashboards.
 */
@Service
@RequiredArgsConstructor
public class DashboardV1ServiceImpl implements DashboardV1Service {

    private static final Set<InvoiceStatus> EXCLUDED_INVOICE_STATUSES = Set.of(
            InvoiceStatus.CANCELLED, InvoiceStatus.WAIVED
    );
    private static final Set<InvoiceStatus> OPEN_INVOICE_STATUSES = Set.of(
            InvoiceStatus.UNPAID, InvoiceStatus.PARTIALLY_PAID, InvoiceStatus.OVERDUE
    );
    private static final Set<ComplaintStatus> OPEN_COMPLAINT_STATUSES = Set.of(
            ComplaintStatus.SUBMITTED, ComplaintStatus.ACKNOWLEDGED, ComplaintStatus.ASSIGNED,
            ComplaintStatus.PENDING, ComplaintStatus.IN_PROGRESS, ComplaintStatus.WAITING_FOR_TENANT,
            ComplaintStatus.REOPENED
    );

    private final AccessGuardService accessGuardService;
    private final RoomRepository roomRepository;
    private final BedRepository bedRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final DepositTransactionRepository depositTransactionRepository;
    private final ElectricityBillRepository electricityBillRepository;
    private final MeterReadingRepository meterReadingRepository;
    private final TenancyRepository tenancyRepository;
    private final ComplaintRepository complaintRepository;
    private final NotificationRepository notificationRepository;
    private final AppProperties appProperties;

    @Override
    @Transactional(readOnly = true)
    @Cacheable(
            cacheNames = CacheConfig.DASHBOARD_METRICS_CACHE,
            key = "#principalEmail + ':' + (#propertyId != null ? #propertyId : 'ALL') + ':' + (#billingYear != null ? #billingYear : 0) + ':' + (#billingMonth != null ? #billingMonth : 0)"
    )
    public StaffDashboardResponseDTO getStaffDashboard(
            String principalEmail,
            UUID propertyId,
            Integer billingYear,
            Integer billingMonth
    ) {
        User currentUser = accessGuardService.loadActiveUser(principalEmail);
        List<UUID> propertyIds = accessGuardService.resolveAccessiblePropertyIds(
                currentUser, Optional.ofNullable(propertyId)
        );
        LocalDate todayIst = LocalDate.now(ZoneId.of(appProperties.getTimezone()));
        int resolvedYear = Optional.ofNullable(billingYear).orElse(todayIst.getYear());
        int resolvedMonth = Optional.ofNullable(billingMonth).orElse(todayIst.getMonthValue());

        if (propertyIds.isEmpty()) {
            return emptyStaffDashboard(resolvedYear, resolvedMonth);
        }

        List<StaffDashboardResponseDTO.PropertyOccupancyPointDTO> propertyOccupancy = buildPropertyOccupancy(propertyIds);
        long totalBeds = propertyOccupancy.stream().mapToLong(StaffDashboardResponseDTO.PropertyOccupancyPointDTO::totalBeds).sum();
        long occupiedBeds = propertyOccupancy.stream().mapToLong(StaffDashboardResponseDTO.PropertyOccupancyPointDTO::occupiedBeds).sum();
        long vacantBeds = propertyOccupancy.stream().mapToLong(StaffDashboardResponseDTO.PropertyOccupancyPointDTO::vacantBeds).sum();
        double occupancyPercent = totalBeds == 0 ? 0.0 : Math.round((occupiedBeds * 1000.0) / totalBeds) / 10.0;

        BigDecimal rentCollected = invoiceRepository.sumActiveCollectedAmount(
                propertyIds, EXCLUDED_INVOICE_STATUSES, true, resolvedYear, true, resolvedMonth
        );
        BigDecimal rentOutstanding = invoiceRepository.sumOutstandingBalance(
                propertyIds, OPEN_INVOICE_STATUSES, false, 0, false, 0
        );
        BigDecimal electricityDue = invoiceRepository.sumOutstandingByType(
                propertyIds, InvoiceType.ELECTRICITY, OPEN_INVOICE_STATUSES
        );
        BigDecimal depositsHeld = computeDepositsHeld(propertyIds);

        List<InvoiceEntity> topOpenInvoices = invoiceRepository.findAccessibleInvoices(
                propertyIds, false, null, false, null, false, 0, false, 0, false, "",
                PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "dueDate"))
        ).getContent().stream().filter(inv -> OPEN_INVOICE_STATUSES.contains(inv.getInvoiceStatus())).toList();

        List<StaffDashboardResponseDTO.DashboardInvoiceDueItemDTO> upcomingDues = topOpenInvoices.stream()
                .filter(inv -> !inv.getDueDate().isBefore(todayIst))
                .limit(8)
                .map(this::toDueItem)
                .toList();

        List<StaffDashboardResponseDTO.DashboardInvoiceDueItemDTO> overdueTenants = topOpenInvoices.stream()
                .filter(inv -> inv.getDueDate().isBefore(todayIst) || inv.getInvoiceStatus() == InvoiceStatus.OVERDUE)
                .limit(8)
                .map(this::toDueItem)
                .toList();

        List<StaffDashboardResponseDTO.DashboardPaymentItemDTO> recentPayments = paymentRepository.findAccessiblePayments(
                        propertyIds, false, null, false, null, false, null, false, null,
                        PageRequest.of(0, 8, Sort.by(Sort.Direction.DESC, "paidAt", "createdAt"))
                ).getContent().stream()
                .map(this::toPaymentItem)
                .toList();

        List<TenancyEntity> recentTenancies = tenancyRepository.findAccessibleTenancies(
                propertyIds, false, null, false, null,
                PageRequest.of(0, 12, Sort.by(Sort.Direction.DESC, "moveInDate", "createdAt"))
        ).getContent();

        List<StaffDashboardResponseDTO.DashboardTenancyEventDTO> recentMoveIns = recentTenancies.stream()
                .limit(6)
                .map(tenancy -> toTenancyEvent(tenancy, tenancy.getMoveInDate()))
                .toList();

        List<StaffDashboardResponseDTO.DashboardTenancyEventDTO> recentMoveOuts = recentTenancies.stream()
                .filter(tenancy -> tenancy.getTenancyStatus() == TenancyStatus.COMPLETED)
                .limit(6)
                .map(tenancy -> toTenancyEvent(tenancy, Optional.ofNullable(tenancy.getActualMoveOutDate()).orElse(tenancy.getMoveInDate())))
                .toList();

        List<StaffDashboardResponseDTO.DashboardComplaintItemDTO> openComplaints = complaintRepository.findAll(
                        (root, query, cb) -> cb.and(
                                root.get("property").get("id").in(propertyIds),
                                root.get("status").in(OPEN_COMPLAINT_STATUSES)
                        ),
                        PageRequest.of(0, 8, Sort.by(Sort.Direction.DESC, "createdAt"))
                ).getContent().stream()
                .map(this::toComplaintItem)
                .toList();

        List<StaffDashboardResponseDTO.DashboardReadingItemDTO> readingsToReview = meterReadingRepository.findAccessibleReadings(
                        propertyIds, false, null, true, MeterReadingStatus.SUBMITTED, false, 0, false, 0, false,
                        PageRequest.of(0, 8, Sort.by(Sort.Direction.DESC, "readingDate"))
                ).getContent().stream()
                .map(reading -> new StaffDashboardResponseDTO.DashboardReadingItemDTO(
                        reading.getId(),
                        reading.getMeter().getMeterNumber(),
                        reading.getProperty().getName(),
                        reading.getUnitsConsumed(),
                        reading.isUnusualUsage(),
                        reading.getReadingStatus().name()
                ))
                .toList();

        return new StaffDashboardResponseDTO(
                resolvedYear,
                resolvedMonth,
                propertyIds.size(),
                roomRepository.countByPropertyIdIn(propertyIds),
                totalBeds,
                occupiedBeds,
                vacantBeds,
                0L,
                0L,
                occupancyPercent,
                rentCollected,
                rentOutstanding,
                electricityDue,
                depositsHeld,
                openComplaints.size(),
                readingsToReview.size(),
                buildMonthlyCollectionSeries(propertyIds),
                propertyOccupancy,
                buildPaymentStatusBreakdown(propertyIds, resolvedYear, resolvedMonth),
                buildElectricitySeries(propertyIds),
                upcomingDues,
                overdueTenants,
                recentPayments,
                recentMoveIns,
                recentMoveOuts,
                openComplaints,
                readingsToReview
        );
    }

    @Override
    @Transactional(readOnly = true)
    public TenantDashboardResponseDTO getTenantDashboard(String principalEmail) {
        User resident = accessGuardService.loadActiveUser(principalEmail);
        OrganizationEntity organization = accessGuardService.resolveUserOrganization(resident);
        String normalizedEmail = resident.getEmail().trim().toLowerCase(Locale.ROOT);

        List<TenancyEntity> tenancies = tenancyRepository.findSelfResidencyTenancies(
                resident.getId(), organization.getId(), normalizedEmail
        );
        TenancyEntity activeStay = tenancies.stream()
                .filter(tenancy -> tenancy.getTenancyStatus() == TenancyStatus.ACTIVE)
                .findFirst()
                .or(() -> tenancies.stream().findFirst())
                .orElse(null);

        List<InvoiceEntity> selfInvoices = invoiceRepository.findSelfInvoices(
                resident.getId(), organization.getId(), normalizedEmail,
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "dueDate"))
        ).getContent();

        List<InvoiceEntity> openBills = selfInvoices.stream()
                .filter(inv -> OPEN_INVOICE_STATUSES.contains(inv.getInvoiceStatus()))
                .toList();
        BigDecimal totalOutstanding = openBills.stream()
                .map(InvoiceEntity::getBalanceDue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal electricityDue = openBills.stream()
                .filter(inv -> inv.getInvoiceType() == InvoiceType.ELECTRICITY)
                .map(InvoiceEntity::getBalanceDue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<StaffDashboardResponseDTO.DashboardPaymentItemDTO> recentPayments = Optional.ofNullable(activeStay)
                .map(stay -> paymentRepository.findAllByTenantIdOrderByPaidAtDescCreatedAtDesc(stay.getTenant().getId())
                        .stream().limit(5).map(this::toPaymentItem).toList())
                .orElse(List.of());

        long openComplaints = complaintRepository.findByMemberOrderByCreatedAtDesc(resident).stream()
                .filter(complaint -> OPEN_COMPLAINT_STATUSES.contains(complaint.getStatus()))
                .count();
        long unreadNotifications = notificationRepository.countByUserIdAndIsReadFalse(resident.getId());

        return new TenantDashboardResponseDTO(
                Optional.ofNullable(activeStay).map(stay -> stay.getTenant().getId()).orElse(null),
                Optional.ofNullable(activeStay).map(stay -> stay.getProperty().getId()).orElse(null),
                Optional.ofNullable(activeStay).map(stay -> stay.getProperty().getName()).orElse(null),
                Optional.ofNullable(activeStay).map(stay -> stay.getRoom().getNumber()).orElse(null),
                Optional.ofNullable(activeStay).map(stay -> stay.getBed().getBedCode()).orElse(null),
                Optional.ofNullable(activeStay).map(TenancyEntity::getAgreedMonthlyRent).orElse(BigDecimal.ZERO),
                Optional.ofNullable(activeStay).map(TenancyEntity::getDepositReceivedAmount).orElse(BigDecimal.ZERO),
                Optional.ofNullable(activeStay).map(TenancyEntity::getMoveInDate).orElse(null),
                totalOutstanding,
                electricityDue,
                openBills.size(),
                openComplaints,
                0L,
                unreadNotifications,
                openBills.stream().limit(5).map(this::toDueItem).toList(),
                recentPayments
        );
    }

    private List<StaffDashboardResponseDTO.PropertyOccupancyPointDTO> buildPropertyOccupancy(List<UUID> propertyIds) {
        Map<UUID, String> names = new LinkedHashMap<>();
        Map<UUID, Map<BedStatus, Long>> countsByProperty = new LinkedHashMap<>();
        bedRepository.countBedsGroupedByPropertyAndStatus(propertyIds).forEach(row -> {
            UUID propId = (UUID) row[0];
            names.put(propId, (String) row[1]);
            BedStatus status = (BedStatus) row[2];
            long count = ((Number) row[3]).longValue();
            countsByProperty.computeIfAbsent(propId, key -> new EnumMap<>(BedStatus.class)).put(status, count);
        });
        return countsByProperty.entrySet().stream()
                .map(entry -> {
                    Map<BedStatus, Long> statusMap = entry.getValue();
                    long total = statusMap.values().stream().mapToLong(Long::longValue).sum();
                    long occupied = statusMap.getOrDefault(BedStatus.OCCUPIED, 0L);
                    long vacant = statusMap.getOrDefault(BedStatus.VACANT, 0L);
                    double pct = total == 0 ? 0.0 : Math.round((occupied * 1000.0) / total) / 10.0;
                    return new StaffDashboardResponseDTO.PropertyOccupancyPointDTO(
                            entry.getKey(), names.get(entry.getKey()), occupied, vacant, total, pct
                    );
                })
                .toList();
    }

    private BigDecimal computeDepositsHeld(List<UUID> propertyIds) {
        BigDecimal collected = depositTransactionRepository.sumByPropertyIdsAndType(propertyIds, DepositTransactionType.COLLECTED);
        BigDecimal deducted = depositTransactionRepository.sumByPropertyIdsAndType(propertyIds, DepositTransactionType.DEDUCTION);
        BigDecimal refunded = depositTransactionRepository.sumByPropertyIdsAndType(propertyIds, DepositTransactionType.REFUND);
        BigDecimal forfeited = depositTransactionRepository.sumByPropertyIdsAndType(propertyIds, DepositTransactionType.FORFEIT);
        return collected.subtract(deducted).subtract(refunded).subtract(forfeited).max(BigDecimal.ZERO);
    }

    private List<StaffDashboardResponseDTO.MonthlyCollectionPointDTO> buildMonthlyCollectionSeries(List<UUID> propertyIds) {
        List<StaffDashboardResponseDTO.MonthlyCollectionPointDTO> points = invoiceRepository
                .sumMonthlyCollectionAndOutstanding(propertyIds, EXCLUDED_INVOICE_STATUSES, OPEN_INVOICE_STATUSES)
                .stream()
                .limit(6)
                .map(row -> new StaffDashboardResponseDTO.MonthlyCollectionPointDTO(
                        formatMonthLabel(((Number) row[0]).intValue(), ((Number) row[1]).intValue()),
                        (BigDecimal) row[2],
                        (BigDecimal) row[3]
                ))
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        Collections.reverse(points);
        return points;
    }

    private List<StaffDashboardResponseDTO.StatusBreakdownPointDTO> buildPaymentStatusBreakdown(
            List<UUID> propertyIds,
            int year,
            int month
    ) {
        return invoiceRepository.sumInvoiceStatusBreakdown(propertyIds, true, year, true, month)
                .stream()
                .map(row -> new StaffDashboardResponseDTO.StatusBreakdownPointDTO(
                        row[0].toString(),
                        ((Number) row[1]).longValue(),
                        (BigDecimal) row[2]
                ))
                .toList();
    }

    private List<StaffDashboardResponseDTO.MonthlyElectricityPointDTO> buildElectricitySeries(List<UUID> propertyIds) {
        List<StaffDashboardResponseDTO.MonthlyElectricityPointDTO> points = electricityBillRepository
                .sumMonthlyConsumption(propertyIds, ElectricityBillStatus.CANCELLED)
                .stream()
                .limit(6)
                .map(row -> new StaffDashboardResponseDTO.MonthlyElectricityPointDTO(
                        formatMonthLabel(((Number) row[0]).intValue(), ((Number) row[1]).intValue()),
                        (BigDecimal) row[2],
                        (BigDecimal) row[3]
                ))
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        Collections.reverse(points);
        return points;
    }

    private String formatMonthLabel(int year, int month) {
        return Month.of(month).getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + (year % 100);
    }

    private StaffDashboardResponseDTO.DashboardInvoiceDueItemDTO toDueItem(InvoiceEntity invoice) {
        return new StaffDashboardResponseDTO.DashboardInvoiceDueItemDTO(
                invoice.getId(),
                invoice.getInvoiceNumber(),
                invoice.getTenant().getFullName(),
                invoice.getProperty().getName(),
                Optional.ofNullable(invoice.getRoom()).map(room -> room.getNumber()).orElse("—"),
                invoice.getDueDate(),
                invoice.getBalanceDue(),
                invoice.getInvoiceStatus().name()
        );
    }

    private StaffDashboardResponseDTO.DashboardPaymentItemDTO toPaymentItem(PaymentEntity payment) {
        return new StaffDashboardResponseDTO.DashboardPaymentItemDTO(
                payment.getId(),
                payment.getReceiptNumber(),
                payment.getTenant().getFullName(),
                payment.getProperty().getName(),
                payment.getAmount(),
                payment.getPaymentMethod().name(),
                payment.getPaidAt().atZone(ZoneId.of(appProperties.getTimezone())).toLocalDate()
        );
    }

    private StaffDashboardResponseDTO.DashboardTenancyEventDTO toTenancyEvent(TenancyEntity tenancy, LocalDate eventDate) {
        return new StaffDashboardResponseDTO.DashboardTenancyEventDTO(
                tenancy.getId(),
                tenancy.getTenant().getFullName(),
                tenancy.getProperty().getName(),
                tenancy.getRoom().getNumber(),
                tenancy.getBed().getBedCode(),
                eventDate,
                tenancy.getTenancyStatus().name()
        );
    }

    private StaffDashboardResponseDTO.DashboardComplaintItemDTO toComplaintItem(Complaint complaint) {
        return new StaffDashboardResponseDTO.DashboardComplaintItemDTO(
                complaint.getId(),
                complaint.getTitle(),
                Optional.ofNullable(complaint.getProperty()).map(prop -> prop.getName()).orElse("—"),
                Optional.ofNullable(complaint.getTenant()).map(tenant -> tenant.getFullName()).orElse(complaint.getMember().getName()),
                complaint.getPriority().name(),
                complaint.getStatus().name()
        );
    }

    private StaffDashboardResponseDTO emptyStaffDashboard(int year, int month) {
        return new StaffDashboardResponseDTO(
                year, month, 0, 0, 0, 0, 0, 0, 0, 0.0,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0,
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of()
        );
    }
}

