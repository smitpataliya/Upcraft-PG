package com.upcraft.stay.search.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.criteria.Predicate;

import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.upcraft.stay.billing.entity.InvoiceEntity;
import com.upcraft.stay.billing.entity.PaymentEntity;
import com.upcraft.stay.billing.repository.InvoiceRepository;
import com.upcraft.stay.billing.repository.PaymentRepository;
import com.upcraft.stay.common.enums.InvoiceStatus;
import com.upcraft.stay.common.enums.TenantStatus;
import com.upcraft.stay.common.exception.ValidationException;
import com.upcraft.stay.domain.entity.Complaint;
import com.upcraft.stay.domain.entity.User;
import com.upcraft.stay.domain.enums.Role;
import com.upcraft.stay.organization.entity.OrganizationEntity;
import com.upcraft.stay.repository.ComplaintRepository;
import com.upcraft.stay.room.entity.BedEntity;
import com.upcraft.stay.room.repository.BedRepository;
import com.upcraft.stay.search.dto.GlobalSearchResponseDTO;
import com.upcraft.stay.search.dto.SearchResultItemDTO;
import com.upcraft.stay.search.enums.SearchCategory;
import com.upcraft.stay.search.service.GlobalSearchService;
import com.upcraft.stay.security.AccessGuardService;
import com.upcraft.stay.tenant.entity.TenancyEntity;
import com.upcraft.stay.tenant.entity.TenantEntity;
import com.upcraft.stay.tenant.repository.TenancyRepository;
import com.upcraft.stay.tenant.repository.TenantRepository;

/**
 * Executes bounded (max 5 per category), indexed, role- and property-scoped global search.
 */
@Service
@RequiredArgsConstructor
public class GlobalSearchServiceImpl implements GlobalSearchService {

    private static final int CATEGORY_LIMIT = 5;
    private static final int MIN_QUERY_LENGTH = 2;
    private static final int MAX_QUERY_LENGTH = 100;

    private final AccessGuardService accessGuardService;
    private final TenantRepository tenantRepository;
    private final BedRepository bedRepository;
    private final TenancyRepository tenancyRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final ComplaintRepository complaintRepository;

    @Override
    @Transactional(readOnly = true)
    public GlobalSearchResponseDTO search(String principalEmail, String queryText, UUID propertyId) {
        String normalized = normalizeQuery(queryText);
        String likePattern = "%" + normalized.toLowerCase(Locale.ROOT) + "%";
        User currentUser = accessGuardService.loadActiveUser(principalEmail);
        if (currentUser.getRole() == Role.PG_MEMBER) {
            return searchResidentScope(currentUser, normalized, likePattern);
        }
        List<UUID> propertyIds = accessGuardService.resolveAccessiblePropertyIds(
                currentUser, Optional.ofNullable(propertyId)
        );
        if (CollectionUtils.isEmpty(propertyIds)) {
            return new GlobalSearchResponseDTO(normalized, List.of(), List.of(), List.of(), List.of(), List.of(), 0);
        }
        return searchStaffScope(propertyIds, normalized, likePattern);
    }

    private GlobalSearchResponseDTO searchStaffScope(List<UUID> propertyIds, String query, String pattern) {
        List<SearchResultItemDTO> tenants = searchStaffTenants(propertyIds, pattern);
        List<SearchResultItemDTO> roomsAndBeds = searchStaffBeds(propertyIds, pattern);
        List<SearchResultItemDTO> invoices = searchStaffInvoices(propertyIds, pattern);
        List<SearchResultItemDTO> payments = searchStaffPayments(propertyIds, pattern);
        List<SearchResultItemDTO> complaints = searchStaffComplaints(propertyIds, pattern);
        int totalHits = tenants.size() + roomsAndBeds.size() + invoices.size() + payments.size() + complaints.size();
        return new GlobalSearchResponseDTO(query, tenants, roomsAndBeds, invoices, payments, complaints, totalHits);
    }

    private GlobalSearchResponseDTO searchResidentScope(User residentUser, String query, String pattern) {
        OrganizationEntity organization = accessGuardService.resolveUserOrganization(residentUser);
        String email = residentUser.getEmail().trim().toLowerCase(Locale.ROOT);
        Pageable topFiveInvoices = PageRequest.of(0, CATEGORY_LIMIT, Sort.by(Sort.Direction.DESC, "dueDate"));
        Pageable topFivePayments = PageRequest.of(0, CATEGORY_LIMIT, Sort.by(Sort.Direction.DESC, "paidAt"));
        Pageable topFiveComplaints = PageRequest.of(0, CATEGORY_LIMIT, Sort.by(Sort.Direction.DESC, "createdAt"));

        List<SearchResultItemDTO> roomsAndBeds = tenancyRepository
                .findSelfResidencyTenancies(residentUser.getId(), organization.getId(), email).stream()
                .filter(tenancy -> matchesTenancyRoom(tenancy, pattern))
                .limit(CATEGORY_LIMIT)
                .map(this::mapSelfTenancy)
                .toList();
        List<SearchResultItemDTO> invoices = invoiceRepository
                .searchSelfInvoices(residentUser.getId(), organization.getId(), email, pattern, topFiveInvoices)
                .getContent().stream().map(inv -> mapInvoice(inv, "/member/bills")).toList();
        List<SearchResultItemDTO> payments = paymentRepository
                .searchSelfPayments(residentUser.getId(), organization.getId(), email, pattern, topFivePayments)
                .getContent().stream().map(pay -> mapPayment(pay, "/member/bills")).toList();
        List<SearchResultItemDTO> complaints = complaintRepository
                .findAll(buildResidentComplaintSpec(residentUser.getId(), pattern), topFiveComplaints)
                .getContent().stream().map(comp -> mapComplaint(comp, "/member/complaints")).toList();

        int totalHits = roomsAndBeds.size() + invoices.size() + payments.size() + complaints.size();
        return new GlobalSearchResponseDTO(query, List.of(), roomsAndBeds, invoices, payments, complaints, totalHits);
    }

    private List<SearchResultItemDTO> searchStaffTenants(List<UUID> propertyIds, String pattern) {
        Pageable pageable = PageRequest.of(0, CATEGORY_LIMIT, Sort.by(Sort.Direction.ASC, "fullName"));
        return tenantRepository.searchAccessibleTenants(propertyIds, false, TenantStatus.ACTIVE, true, pattern, pageable)
                .getContent().stream().map(this::mapTenant).toList();
    }

    private List<SearchResultItemDTO> searchStaffBeds(List<UUID> propertyIds, String pattern) {
        Pageable pageable = PageRequest.of(0, CATEGORY_LIMIT, Sort.by(Sort.Direction.ASC, "bedCode"));
        return bedRepository.searchAccessibleBeds(propertyIds, pattern, pageable)
                .getContent().stream().map(this::mapBed).toList();
    }

    private List<SearchResultItemDTO> searchStaffInvoices(List<UUID> propertyIds, String pattern) {
        Pageable pageable = PageRequest.of(0, CATEGORY_LIMIT, Sort.by(Sort.Direction.DESC, "dueDate"));
        return invoiceRepository.findAccessibleInvoices(
                        propertyIds, false, InvoiceStatus.UNPAID, false, UUID.randomUUID(),
                        false, 0, false, 0, true, pattern, pageable)
                .getContent().stream().map(inv -> mapInvoice(inv, "STAFF_BILLING")).toList();
    }

    private List<SearchResultItemDTO> searchStaffPayments(List<UUID> propertyIds, String pattern) {
        Pageable pageable = PageRequest.of(0, CATEGORY_LIMIT, Sort.by(Sort.Direction.DESC, "paidAt"));
        return paymentRepository.searchAccessiblePayments(propertyIds, pattern, pageable)
                .getContent().stream().map(pay -> mapPayment(pay, "STAFF_BILLING")).toList();
    }

    private List<SearchResultItemDTO> searchStaffComplaints(List<UUID> propertyIds, String pattern) {
        Pageable pageable = PageRequest.of(0, CATEGORY_LIMIT, Sort.by(Sort.Direction.DESC, "createdAt"));
        return complaintRepository.findAll(buildStaffComplaintSpec(propertyIds, pattern), pageable)
                .getContent().stream().map(comp -> mapComplaint(comp, "STAFF_COMPLAINTS")).toList();
    }

    private Specification<Complaint> buildStaffComplaintSpec(List<UUID> propertyIds, String pattern) {
        return (root, query, builder) -> {
            Predicate propertyMatch = root.get("property").get("id").in(propertyIds);
            Predicate textMatch = builder.or(
                    builder.like(builder.lower(root.get("title")), pattern),
                    builder.like(builder.lower(root.get("description")), pattern)
            );
            return builder.and(propertyMatch, textMatch);
        };
    }

    private Specification<Complaint> buildResidentComplaintSpec(UUID memberId, String pattern) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("member").get("id"), memberId));
            predicates.add(builder.or(
                    builder.like(builder.lower(root.get("title")), pattern),
                    builder.like(builder.lower(root.get("description")), pattern)
            ));
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private SearchResultItemDTO mapTenant(TenantEntity tenant) {
        return new SearchResultItemDTO(
                tenant.getId(),
                SearchCategory.TENANT,
                tenant.getFullName(),
                String.format("%s · %s", tenant.getPhone(), tenant.getProperty().getName()),
                tenant.getProperty().getId(),
                tenant.getProperty().getName(),
                "STAFF_TENANTS",
                tenant.getTenantStatus().name()
        );
    }

    private SearchResultItemDTO mapBed(BedEntity bedEntity) {
        return new SearchResultItemDTO(
                bedEntity.getId(),
                SearchCategory.ROOM_BED,
                String.format("Room %s · %s", bedEntity.getRoom().getNumber(), bedEntity.getBedCode()),
                String.format("%s · %s", bedEntity.getRoom().getRoomType(), bedEntity.getProperty().getName()),
                bedEntity.getProperty().getId(),
                bedEntity.getProperty().getName(),
                "STAFF_ROOMS",
                bedEntity.getBedStatus().name()
        );
    }

    private SearchResultItemDTO mapSelfTenancy(TenancyEntity tenancy) {
        return new SearchResultItemDTO(
                tenancy.getId(),
                SearchCategory.ROOM_BED,
                String.format("Room %s · %s", tenancy.getRoom().getNumber(), tenancy.getBed().getBedCode()),
                tenancy.getProperty().getName(),
                tenancy.getProperty().getId(),
                tenancy.getProperty().getName(),
                "/member/room",
                tenancy.getTenancyStatus().name()
        );
    }

    private SearchResultItemDTO mapInvoice(InvoiceEntity invoice, String routeHint) {
        return new SearchResultItemDTO(
                invoice.getId(),
                SearchCategory.INVOICE,
                invoice.getInvoiceNumber(),
                String.format("%s · Due %s · ₹%s", invoice.getTenant().getFullName(), invoice.getDueDate(), invoice.getBalanceDue()),
                invoice.getProperty().getId(),
                invoice.getProperty().getName(),
                routeHint,
                invoice.getInvoiceStatus().name()
        );
    }

    private SearchResultItemDTO mapPayment(PaymentEntity payment, String routeHint) {
        return new SearchResultItemDTO(
                payment.getId(),
                SearchCategory.PAYMENT,
                payment.getReceiptNumber(),
                String.format("%s · %s · ₹%s", payment.getTenant().getFullName(), payment.getPaymentMethod(), payment.getAmount()),
                payment.getProperty().getId(),
                payment.getProperty().getName(),
                routeHint,
                payment.getPaymentStatus().name()
        );
    }

    private SearchResultItemDTO mapComplaint(Complaint complaint, String routeHint) {
        UUID propId = Optional.ofNullable(complaint.getProperty()).map(prop -> prop.getId()).orElse(null);
        String propName = Optional.ofNullable(complaint.getProperty()).map(prop -> prop.getName()).orElse("Property");
        return new SearchResultItemDTO(
                complaint.getId(),
                SearchCategory.COMPLAINT,
                complaint.getTitle(),
                String.format("%s · %s", complaint.getCategory().name(), propName),
                propId,
                propName,
                routeHint,
                complaint.getStatus().name()
        );
    }

    private boolean matchesTenancyRoom(TenancyEntity tenancy, String likePattern) {
        String term = likePattern.replace("%", "");
        return tenancy.getRoom().getNumber().toLowerCase(Locale.ROOT).contains(term)
                || tenancy.getBed().getBedCode().toLowerCase(Locale.ROOT).contains(term)
                || tenancy.getProperty().getName().toLowerCase(Locale.ROOT).contains(term);
    }

    private String normalizeQuery(String rawQuery) {
        String trimmed = StringUtils.trimToEmpty(rawQuery);
        if (trimmed.length() < MIN_QUERY_LENGTH || trimmed.length() > MAX_QUERY_LENGTH) {
            throw new ValidationException(
                    String.format("Search query must be between %d and %d characters", MIN_QUERY_LENGTH, MAX_QUERY_LENGTH)
            );
        }
        return trimmed;
    }
}

