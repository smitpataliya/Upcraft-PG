package com.upcraft.stay.reporting.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.criteria.Predicate;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import com.upcraft.stay.common.enums.BedStatus;
import com.upcraft.stay.domain.entity.Complaint;
import com.upcraft.stay.domain.entity.User;
import com.upcraft.stay.organization.entity.OrganizationEntity;
import com.upcraft.stay.reporting.dto.ReportResponseDTO;
import com.upcraft.stay.reporting.enums.ReportType;
import com.upcraft.stay.repository.ComplaintRepository;
import com.upcraft.stay.room.entity.BedEntity;
import com.upcraft.stay.room.repository.BedRepository;
import com.upcraft.stay.tenant.entity.TenancyEntity;
import com.upcraft.stay.tenant.entity.TenantEntity;
import com.upcraft.stay.tenant.repository.TenancyRepository;
import com.upcraft.stay.tenant.repository.TenantRepository;

/**
 * Builds operational report payloads (occupancy, tenants, move-in/out, complaints).
 */
@Component
@RequiredArgsConstructor
public class OperationsReportBuilder {

    private final BedRepository bedRepository;
    private final TenantRepository tenantRepository;
    private final TenancyRepository tenancyRepository;
    private final ComplaintRepository complaintRepository;
    private final ReportSupport support;

    public ReportResponseDTO buildOccupancy(
            OrganizationEntity organization,
            String scopeLabel,
            List<UUID> propertyIds,
            LocalDate fromDate,
            LocalDate toDate,
            User actorUser,
            int page,
            Integer size,
            boolean exportAll
    ) {
        Pageable pageable = support.buildReportPageable(page, size, exportAll, Sort.by(Sort.Direction.ASC, "bedCode"));
        Page<BedEntity> bedPage = bedRepository.findAvailability(
                propertyIds, false, null, false, null, false, null, false, null, pageable
        );
        List<Map<String, Object>> rows = bedPage.getContent().stream().map(this::mapOccupancyRow).toList();
        long occupiedCount = bedPage.getContent().stream()
                .filter(bedEntity -> bedEntity.getBedStatus() == BedStatus.OCCUPIED).count();
        long vacantCount = bedPage.getContent().stream()
                .filter(bedEntity -> bedEntity.getBedStatus() == BedStatus.VACANT).count();
        return support.buildResponse(
                ReportType.OCCUPANCY,
                "Bed Occupancy & Vacancy Report",
                organization,
                scopeLabel,
                fromDate,
                toDate,
                actorUser,
                List.of("property", "room", "roomType", "bedCode", "monthlyRent", "defaultDeposit", "status"),
                rows,
                Map.of("occupiedBeds", BigDecimal.valueOf(occupiedCount), "vacantBeds", BigDecimal.valueOf(vacantCount)),
                bedPage
        );
    }

    public ReportResponseDTO buildTenants(
            OrganizationEntity organization,
            String scopeLabel,
            List<UUID> propertyIds,
            LocalDate fromDate,
            LocalDate toDate,
            User actorUser,
            int page,
            Integer size,
            boolean exportAll
    ) {
        Pageable pageable = support.buildReportPageable(page, size, exportAll, Sort.by(Sort.Direction.ASC, "fullName"));
        Page<TenantEntity> tenantPage = tenantRepository.searchAccessibleTenants(
                propertyIds, false, null, false, "", pageable
        );
        List<Map<String, Object>> rows = tenantPage.getContent().stream().map(this::mapTenantRow).toList();
        return support.buildResponse(
                ReportType.TENANTS,
                "Tenant Directory Report",
                organization,
                scopeLabel,
                fromDate,
                toDate,
                actorUser,
                List.of("property", "fullName", "phone", "email", "emergencyContact", "status"),
                rows,
                Map.of("tenantsOnPage", BigDecimal.valueOf(rows.size())),
                tenantPage
        );
    }

    public ReportResponseDTO buildMoveInOut(
            OrganizationEntity organization,
            String scopeLabel,
            List<UUID> propertyIds,
            LocalDate fromDate,
            LocalDate toDate,
            User actorUser,
            int page,
            Integer size,
            boolean exportAll
    ) {
        Pageable pageable = support.buildReportPageable(page, size, exportAll, Sort.by(Sort.Direction.DESC, "moveInDate"));
        Page<TenancyEntity> tenancyPage = tenancyRepository.findReportMoveInOut(
                propertyIds,
                Optional.ofNullable(fromDate).isPresent(), fromDate,
                Optional.ofNullable(toDate).isPresent(), toDate, pageable
        );
        List<Map<String, Object>> rows = tenancyPage.getContent().stream().map(this::mapMoveInOutRow).toList();
        BigDecimal totalRefundable = tenancyPage.getContent().stream()
                .map(TenancyEntity::getRefundableDepositAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return support.buildResponse(
                ReportType.MOVE_IN_OUT,
                "Move-In & Move-Out Movement Report",
                organization,
                scopeLabel,
                fromDate,
                toDate,
                actorUser,
                List.of("property", "tenant", "roomBed", "moveInDate", "moveOutDate", "monthlyRent", "refundableDeposit", "status"),
                rows,
                Map.of("tenanciesCount", BigDecimal.valueOf(rows.size()), "refundableDeposits", totalRefundable),
                tenancyPage
        );
    }

    public ReportResponseDTO buildComplaints(
            OrganizationEntity organization,
            String scopeLabel,
            List<UUID> propertyIds,
            LocalDate fromDate,
            LocalDate toDate,
            User actorUser,
            int page,
            Integer size,
            boolean exportAll
    ) {
        Pageable pageable = support.buildReportPageable(page, size, exportAll, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Complaint> complaintPage = complaintRepository.findAll(
                buildComplaintSpec(propertyIds, fromDate, toDate), pageable
        );
        List<Map<String, Object>> rows = complaintPage.getContent().stream().map(this::mapComplaintRow).toList();
        return support.buildResponse(
                ReportType.COMPLAINTS,
                "Maintenance & Complaints Report",
                organization,
                scopeLabel,
                fromDate,
                toDate,
                actorUser,
                List.of("title", "property", "category", "priority", "status", "assignedTo", "createdAt"),
                rows,
                Map.of("complaintsCount", BigDecimal.valueOf(rows.size())),
                complaintPage
        );
    }

    private Specification<Complaint> buildComplaintSpec(List<UUID> propertyIds, LocalDate fromDate, LocalDate toDate) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(root.get("property").get("id").in(propertyIds));
            Optional.ofNullable(fromDate)
                    .map(date -> LocalDateTime.of(date, LocalTime.MIN))
                    .ifPresent(start -> predicates.add(builder.greaterThanOrEqualTo(root.get("createdAt"), start)));
            Optional.ofNullable(toDate)
                    .map(date -> LocalDateTime.of(date, LocalTime.MAX))
                    .ifPresent(end -> predicates.add(builder.lessThanOrEqualTo(root.get("createdAt"), end)));
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Map<String, Object> mapOccupancyRow(BedEntity bedEntity) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("property", bedEntity.getProperty().getName());
        row.put("room", bedEntity.getRoom().getNumber());
        row.put("roomType", bedEntity.getRoom().getRoomType());
        row.put("bedCode", bedEntity.getBedCode());
        row.put("monthlyRent", bedEntity.getMonthlyRent());
        row.put("defaultDeposit", bedEntity.getDefaultDeposit());
        row.put("status", bedEntity.getBedStatus().name());
        return row;
    }

    private Map<String, Object> mapTenantRow(TenantEntity tenantEntity) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("property", tenantEntity.getProperty().getName());
        row.put("fullName", tenantEntity.getFullName());
        row.put("phone", tenantEntity.getPhone());
        row.put("email", StringUtils.defaultIfBlank(tenantEntity.getEmail(), "-"));
        row.put("emergencyContact", StringUtils.defaultIfBlank(tenantEntity.getEmergencyContact(), "-"));
        row.put("status", tenantEntity.getTenantStatus().name());
        return row;
    }

    private Map<String, Object> mapMoveInOutRow(TenancyEntity tenancyEntity) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("property", tenancyEntity.getProperty().getName());
        row.put("tenant", tenancyEntity.getTenant().getFullName());
        row.put("roomBed", String.format("R%s / %s", tenancyEntity.getRoom().getNumber(), tenancyEntity.getBed().getBedCode()));
        row.put("moveInDate", tenancyEntity.getMoveInDate().toString());
        row.put("moveOutDate", Optional.ofNullable(tenancyEntity.getActualMoveOutDate()).map(LocalDate::toString).orElse("-"));
        row.put("monthlyRent", tenancyEntity.getAgreedMonthlyRent());
        row.put("refundableDeposit", tenancyEntity.getRefundableDepositAmount());
        row.put("status", tenancyEntity.getTenancyStatus().name());
        return row;
    }

    private Map<String, Object> mapComplaintRow(Complaint complaint) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("title", complaint.getTitle());
        row.put("property", Optional.ofNullable(complaint.getProperty()).map(property -> property.getName()).orElse("-"));
        row.put("category", complaint.getCategory().name());
        row.put("priority", complaint.getPriority().name());
        row.put("status", complaint.getStatus().name());
        row.put("assignedTo", Optional.ofNullable(complaint.getAssignedToUser()).map(User::getName).orElse("Unassigned"));
        row.put("createdAt", Optional.ofNullable(complaint.getCreatedAt()).map(LocalDateTime::toString).orElse("-"));
        return row;
    }
}

