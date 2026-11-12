package com.upcraft.stay.operations.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.criteria.Predicate;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.upcraft.stay.billing.entity.InvoiceEntity;
import com.upcraft.stay.billing.entity.PaymentEntity;
import com.upcraft.stay.billing.repository.InvoiceRepository;
import com.upcraft.stay.billing.repository.PaymentRepository;
import com.upcraft.stay.common.config.AppProperties;
import com.upcraft.stay.common.dto.PageResponseDTO;
import com.upcraft.stay.common.exception.ForbiddenException;
import com.upcraft.stay.common.exception.ResourceNotFoundException;
import com.upcraft.stay.common.util.PaginationUtils;
import com.upcraft.stay.domain.entity.User;
import com.upcraft.stay.domain.enums.Role;
import com.upcraft.stay.operations.dto.CreateDocumentRequestDTO;
import com.upcraft.stay.operations.dto.DocumentResponseDTO;
import com.upcraft.stay.operations.entity.DocumentEntity;
import com.upcraft.stay.operations.enums.DocumentCategory;
import com.upcraft.stay.operations.enums.DocumentScopeType;
import com.upcraft.stay.operations.enums.NotificationEventType;
import com.upcraft.stay.operations.mapper.OperationsMapper;
import com.upcraft.stay.operations.repository.DocumentRepository;
import com.upcraft.stay.operations.service.DocumentV1Service;
import com.upcraft.stay.operations.service.NotificationDispatchService;
import com.upcraft.stay.organization.entity.OrganizationEntity;
import com.upcraft.stay.property.entity.PropertyEntity;
import com.upcraft.stay.security.AccessGuardService;
import com.upcraft.stay.storage.entity.StoredFileEntity;
import com.upcraft.stay.storage.repository.StoredFileRepository;
import com.upcraft.stay.tenant.entity.TenancyEntity;
import com.upcraft.stay.tenant.entity.TenantEntity;
import com.upcraft.stay.tenant.repository.TenancyRepository;
import com.upcraft.stay.tenant.repository.TenantRepository;

/**
 * Implements private multi-scope document linking, isolation, and resident sharing notifications.
 */
@Service
@RequiredArgsConstructor
public class DocumentV1ServiceImpl implements DocumentV1Service {

    private static final Set<String> SORT_FIELDS = Set.of("createdAt", "title", "documentCategory");

    private final AccessGuardService accessGuardService;
    private final DocumentRepository documentRepository;
    private final StoredFileRepository storedFileRepository;
    private final TenantRepository tenantRepository;
    private final TenancyRepository tenancyRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final NotificationDispatchService notificationDispatchService;
    private final OperationsMapper operationsMapper;
    private final AppProperties appProperties;

    @Override
    @Transactional(readOnly = true)
    public PageResponseDTO<DocumentResponseDTO> listDocuments(
            String principalEmail,
            UUID propertyId,
            DocumentScopeType scopeType,
            DocumentCategory documentCategory,
            UUID tenantId,
            UUID tenancyId,
            int page,
            Integer size
    ) {
        User currentUser = accessGuardService.loadActiveUser(principalEmail);
        OrganizationEntity organization = accessGuardService.resolveUserOrganization(currentUser);
        Pageable pageable = PaginationUtils.createPageable(
                page, size, "createdAt", "DESC", SORT_FIELDS, "createdAt", appProperties
        );

        if (currentUser.getRole() == Role.PG_MEMBER) {
            return listResidentDocuments(currentUser, organization, scopeType, documentCategory, pageable);
        }

        List<UUID> accessiblePropertyIds = accessGuardService.resolveAccessiblePropertyIds(
                currentUser, Optional.ofNullable(propertyId)
        );
        Specification<DocumentEntity> spec = buildStaffSpecification(
                organization.getId(), accessiblePropertyIds, currentUser.getRole() == Role.PG_OWNER,
                scopeType, documentCategory, tenantId, tenancyId
        );
        return PageResponseDTO.from(documentRepository.findAll(spec, pageable).map(operationsMapper::toDocumentResponse));
    }

    @Override
    @Transactional
    public DocumentResponseDTO createDocument(String principalEmail, CreateDocumentRequestDTO request) {
        User currentUser = accessGuardService.loadActiveUser(principalEmail);
        OrganizationEntity organization = accessGuardService.resolveUserOrganization(currentUser);
        StoredFileEntity storedFile = storedFileRepository.findWithDetailsById(request.storedFileId())
                .orElseThrow(() -> new ResourceNotFoundException("Uploaded stored file not found"));
        if (!storedFile.getOrganization().getId().equals(organization.getId())) {
            throw new ForbiddenException("Stored file belongs to another organization");
        }

        if (currentUser.getRole() == Role.PG_MEMBER) {
            return createResidentDocument(currentUser, organization, storedFile, request);
        }

        PropertyEntity property = resolveStaffProperty(currentUser, request.propertyId(), request.scopeType());
        TenantEntity tenant = resolveTenant(organization, request.tenantId());
        TenancyEntity tenancy = resolveTenancy(organization, request.tenancyId());
        InvoiceEntity invoice = resolveInvoice(organization, request.invoiceId());
        PaymentEntity payment = resolvePayment(organization, request.paymentId());

        PropertyEntity effectiveProperty = Optional.ofNullable(property)
                .or(() -> Optional.ofNullable(tenancy).map(TenancyEntity::getProperty))
                .or(() -> Optional.ofNullable(tenant).map(TenantEntity::getProperty))
                .orElse(null);
        TenantEntity effectiveTenant = Optional.ofNullable(tenant)
                .or(() -> Optional.ofNullable(tenancy).map(TenancyEntity::getTenant))
                .or(() -> Optional.ofNullable(invoice).map(InvoiceEntity::getTenant))
                .orElse(null);

        DocumentEntity entity = new DocumentEntity();
        entity.setOrganization(organization);
        entity.setProperty(effectiveProperty);
        entity.setStoredFile(storedFile);
        entity.setScopeType(request.scopeType());
        entity.setDocumentCategory(request.documentCategory());
        entity.setTitle(request.title().trim());
        entity.setDescription(StringUtils.trimToNull(request.description()));
        entity.setTenant(effectiveTenant);
        entity.setTenancy(tenancy);
        entity.setInvoice(invoice);
        entity.setPayment(payment);
        entity.setSharedWithTenant(request.sharedWithTenant());
        entity.setUploadedByUser(currentUser);

        DocumentEntity saved = documentRepository.save(entity);
        notifyTenantIfShared(saved);
        return operationsMapper.toDocumentResponse(saved);
    }

    @Override
    @Transactional
    public void deleteDocument(String principalEmail, UUID documentId) {
        User currentUser = accessGuardService.loadActiveUser(principalEmail);
        DocumentEntity document = documentRepository.findWithDetailsById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));
        OrganizationEntity organization = accessGuardService.resolveUserOrganization(currentUser);
        if (!document.getOrganization().getId().equals(organization.getId())) {
            throw new ForbiddenException("Document is outside your organization scope");
        }
        if (currentUser.getRole() == Role.PG_MEMBER) {
            if (!document.getUploadedByUser().getId().equals(currentUser.getId())) {
                throw new ForbiddenException("Residents can only delete their own uploaded documents");
            }
        } else if (Optional.ofNullable(document.getProperty()).isPresent()) {
            accessGuardService.requirePropertyManagementAccess(currentUser, document.getProperty().getId(), "TENANTS");
        } else if (currentUser.getRole() != Role.PG_OWNER) {
            throw new ForbiddenException("Only PG owners can remove organization-level documents");
        }
        document.markDeleted(currentUser.getEmail(), null);
        documentRepository.save(document);
    }

    private PageResponseDTO<DocumentResponseDTO> listResidentDocuments(
            User resident,
            OrganizationEntity organization,
            DocumentScopeType scopeType,
            DocumentCategory category,
            Pageable pageable
    ) {
        List<TenancyEntity> tenancies = tenancyRepository.findSelfResidencyTenancies(
                resident.getId(), organization.getId(), resident.getEmail().trim().toLowerCase(Locale.ROOT)
        );
        List<UUID> residentTenantIds = tenancies.stream().map(tenancy -> tenancy.getTenant().getId()).distinct().toList();
        List<UUID> residentPropertyIds = tenancies.stream().map(tenancy -> tenancy.getProperty().getId()).distinct().toList();

        Specification<DocumentEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("organization").get("id"), organization.getId()));
            List<Predicate> accessBranches = new ArrayList<>();
            accessBranches.add(cb.equal(root.get("uploadedByUser").get("id"), resident.getId()));
            if (!residentTenantIds.isEmpty()) {
                accessBranches.add(cb.and(
                        cb.isTrue(root.get("sharedWithTenant")),
                        root.get("tenant").get("id").in(residentTenantIds)
                ));
            }
            if (!residentPropertyIds.isEmpty()) {
                accessBranches.add(cb.and(
                        cb.isTrue(root.get("sharedWithTenant")),
                        cb.equal(root.get("scopeType"), DocumentScopeType.PROPERTY),
                        root.get("property").get("id").in(residentPropertyIds)
                ));
            }
            accessBranches.add(cb.and(
                    cb.isTrue(root.get("sharedWithTenant")),
                    cb.equal(root.get("scopeType"), DocumentScopeType.ORGANIZATION)
            ));
            predicates.add(cb.or(accessBranches.toArray(Predicate[]::new)));
            Optional.ofNullable(scopeType).ifPresent(val -> predicates.add(cb.equal(root.get("scopeType"), val)));
            Optional.ofNullable(category).ifPresent(val -> predicates.add(cb.equal(root.get("documentCategory"), val)));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return PageResponseDTO.from(documentRepository.findAll(spec, pageable).map(operationsMapper::toDocumentResponse));
    }

    private DocumentResponseDTO createResidentDocument(
            User resident,
            OrganizationEntity organization,
            StoredFileEntity storedFile,
            CreateDocumentRequestDTO request
    ) {
        if (!storedFile.getUploadedByUser().getId().equals(resident.getId())) {
            throw new ForbiddenException("Residents can only attach files they uploaded");
        }
        TenancyEntity residency = tenancyRepository.findSelfResidencyTenancies(
                        resident.getId(), organization.getId(), resident.getEmail().trim().toLowerCase(Locale.ROOT)
                ).stream()
                .findFirst()
                .orElseThrow(() -> new ForbiddenException("No tenancy found for resident document upload"));

        DocumentEntity entity = new DocumentEntity();
        entity.setOrganization(organization);
        entity.setProperty(residency.getProperty());
        entity.setStoredFile(storedFile);
        entity.setScopeType(DocumentScopeType.TENANT);
        entity.setDocumentCategory(request.documentCategory());
        entity.setTitle(request.title().trim());
        entity.setDescription(StringUtils.trimToNull(request.description()));
        entity.setTenant(residency.getTenant());
        entity.setTenancy(residency);
        entity.setSharedWithTenant(true);
        entity.setUploadedByUser(resident);
        return operationsMapper.toDocumentResponse(documentRepository.save(entity));
    }

    private PropertyEntity resolveStaffProperty(User currentUser, UUID propertyId, DocumentScopeType scopeType) {
        if (scopeType == DocumentScopeType.ORGANIZATION && Optional.ofNullable(propertyId).isEmpty()) {
            if (currentUser.getRole() != Role.PG_OWNER) {
                throw new ForbiddenException("Only PG owners can upload organization-wide documents");
            }
            return null;
        }
        if (Optional.ofNullable(propertyId).isEmpty()) {
            return null;
        }
        return accessGuardService.requirePropertyManagementAccess(currentUser, propertyId, "TENANTS");
    }

    private TenantEntity resolveTenant(OrganizationEntity org, UUID tenantId) {
        if (Optional.ofNullable(tenantId).isEmpty()) {
            return null;
        }
        TenantEntity tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
        if (!tenant.getOrganization().getId().equals(org.getId())) {
            throw new ForbiddenException("Tenant belongs to another organization");
        }
        return tenant;
    }

    private TenancyEntity resolveTenancy(OrganizationEntity org, UUID tenancyId) {
        if (Optional.ofNullable(tenancyId).isEmpty()) {
            return null;
        }
        TenancyEntity tenancy = tenancyRepository.findWithDetailsById(tenancyId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenancy not found"));
        if (!tenancy.getOrganization().getId().equals(org.getId())) {
            throw new ForbiddenException("Tenancy belongs to another organization");
        }
        return tenancy;
    }

    private InvoiceEntity resolveInvoice(OrganizationEntity org, UUID invoiceId) {
        if (Optional.ofNullable(invoiceId).isEmpty()) {
            return null;
        }
        InvoiceEntity invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));
        if (!invoice.getOrganization().getId().equals(org.getId())) {
            throw new ForbiddenException("Invoice belongs to another organization");
        }
        return invoice;
    }

    private PaymentEntity resolvePayment(OrganizationEntity org, UUID paymentId) {
        if (Optional.ofNullable(paymentId).isEmpty()) {
            return null;
        }
        PaymentEntity payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
        if (!payment.getOrganization().getId().equals(org.getId())) {
            throw new ForbiddenException("Payment belongs to another organization");
        }
        return payment;
    }

    private void notifyTenantIfShared(DocumentEntity document) {
        if (!document.isSharedWithTenant() || Optional.ofNullable(document.getTenant()).isEmpty()) {
            return;
        }
        Optional.ofNullable(document.getTenant().getUser()).ifPresent(user ->
                notificationDispatchService.dispatchToUsers(
                        document.getOrganization(), document.getProperty(), List.of(user),
                        NotificationEventType.DOCUMENT_SHARED,
                        "New Document Shared: " + document.getTitle(),
                        "A private document (" + document.getDocumentCategory().name() + ") is now available in your portal.",
                        "DOCUMENT", document.getId()
                ));
    }

    private Specification<DocumentEntity> buildStaffSpecification(
            UUID organizationId,
            List<UUID> accessiblePropertyIds,
            boolean isOwner,
            DocumentScopeType scopeType,
            DocumentCategory category,
            UUID tenantId,
            UUID tenancyId
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("organization").get("id"), organizationId));
            if (!isOwner) {
                if (accessiblePropertyIds.isEmpty()) {
                    predicates.add(cb.disjunction());
                } else {
                    predicates.add(root.get("property").get("id").in(accessiblePropertyIds));
                }
            } else if (!accessiblePropertyIds.isEmpty()) {
                predicates.add(cb.or(
                        cb.isNull(root.get("property")),
                        root.get("property").get("id").in(accessiblePropertyIds)
                ));
            }
            Optional.ofNullable(scopeType).ifPresent(val -> predicates.add(cb.equal(root.get("scopeType"), val)));
            Optional.ofNullable(category).ifPresent(val -> predicates.add(cb.equal(root.get("documentCategory"), val)));
            Optional.ofNullable(tenantId).ifPresent(val -> predicates.add(cb.equal(root.get("tenant").get("id"), val)));
            Optional.ofNullable(tenancyId).ifPresent(val -> predicates.add(cb.equal(root.get("tenancy").get("id"), val)));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
