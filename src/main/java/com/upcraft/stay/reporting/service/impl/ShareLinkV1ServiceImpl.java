package com.upcraft.stay.reporting.service.impl;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.upcraft.stay.audit.dto.AuditLogCommand;
import com.upcraft.stay.audit.enums.AuditAction;
import com.upcraft.stay.audit.enums.AuditEntityType;
import com.upcraft.stay.audit.service.AuditLogService;
import com.upcraft.stay.billing.dto.InvoiceDetailResponseDTO;
import com.upcraft.stay.billing.dto.PaymentReceiptResponseDTO;
import com.upcraft.stay.billing.entity.DepositTransactionEntity;
import com.upcraft.stay.billing.entity.InvoiceEntity;
import com.upcraft.stay.billing.entity.InvoiceItemEntity;
import com.upcraft.stay.billing.entity.PaymentEntity;
import com.upcraft.stay.billing.repository.DepositTransactionRepository;
import com.upcraft.stay.billing.repository.InvoiceItemRepository;
import com.upcraft.stay.billing.repository.InvoiceRepository;
import com.upcraft.stay.billing.repository.PaymentRepository;
import com.upcraft.stay.billing.service.DepositService;
import com.upcraft.stay.billing.service.InvoiceService;
import com.upcraft.stay.billing.service.PaymentService;
import com.upcraft.stay.common.config.AppProperties;
import com.upcraft.stay.common.dto.PageResponseDTO;
import com.upcraft.stay.common.enums.BedStatus;
import com.upcraft.stay.common.enums.DepositTransactionType;
import com.upcraft.stay.common.exception.ForbiddenException;
import com.upcraft.stay.common.exception.ResourceNotFoundException;
import com.upcraft.stay.common.exception.ValidationException;
import com.upcraft.stay.common.util.HashUtils;
import com.upcraft.stay.domain.entity.User;
import com.upcraft.stay.domain.enums.Role;
import com.upcraft.stay.organization.entity.OrganizationEntity;
import com.upcraft.stay.property.entity.PropertyEntity;
import com.upcraft.stay.property.repository.PropertyRepository;
import com.upcraft.stay.reporting.dto.CreateShareLinkRequestDTO;
import com.upcraft.stay.reporting.dto.PublicShareViewResponseDTO;
import com.upcraft.stay.reporting.dto.ShareLinkResponseDTO;
import com.upcraft.stay.reporting.entity.ShareLinkEntity;
import com.upcraft.stay.reporting.enums.ShareResourceType;
import com.upcraft.stay.reporting.repository.ShareLinkRepository;
import com.upcraft.stay.reporting.service.PdfExportService;
import com.upcraft.stay.reporting.service.ShareLinkV1Service;
import com.upcraft.stay.room.entity.BedEntity;
import com.upcraft.stay.room.repository.BedRepository;
import com.upcraft.stay.security.AccessGuardService;
import com.upcraft.stay.tenant.entity.TenancyEntity;
import com.upcraft.stay.tenant.repository.TenancyRepository;

/**
 * Manages hashed expiring capability links and non-PII public vacancy/document sharing.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShareLinkV1ServiceImpl implements ShareLinkV1Service {

    private static final int PUBLIC_VACANCY_LIMIT = 100;

    private final AccessGuardService accessGuardService;
    private final ShareLinkRepository shareLinkRepository;
    private final PropertyRepository propertyRepository;
    private final InvoiceService invoiceService;
    private final PaymentService paymentService;
    private final DepositService depositService;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceItemRepository invoiceItemRepository;
    private final PaymentRepository paymentRepository;
    private final TenancyRepository tenancyRepository;
    private final DepositTransactionRepository depositTransactionRepository;
    private final BedRepository bedRepository;
    private final PdfExportService pdfExportService;
    private final AppProperties appProperties;
    private final ReportSupport reportSupport;
    private final AuditLogService auditLogService;

    @Override
    @Transactional
    public ShareLinkResponseDTO createShareLink(String principalEmail, CreateShareLinkRequestDTO requestDTO) {
        User actorUser = accessGuardService.loadActiveUser(principalEmail);
        OrganizationEntity organization = accessGuardService.resolveUserOrganization(actorUser);
        PropertyEntity propertyEntity = resolveAndAuthorizeTargetProperty(actorUser, principalEmail, requestDTO);
        int expiryHours = resolveExpiryHours(requestDTO.expiryHours());

        String rawToken = HashUtils.generateUrlSafeToken();
        ShareLinkEntity entity = new ShareLinkEntity();
        entity.setOrganization(organization);
        entity.setProperty(propertyEntity);
        entity.setCreatedByUser(actorUser);
        entity.setTokenHash(HashUtils.sha256Hex(rawToken));
        entity.setResourceType(requestDTO.resourceType());
        entity.setResourceId(requestDTO.resourceId());
        entity.setExpiresAt(Instant.now().plus(expiryHours, ChronoUnit.HOURS));

        ShareLinkEntity savedEntity = shareLinkRepository.save(entity);
        auditLogService.recordEvent(new AuditLogCommand(
                organization, propertyEntity, actorUser, principalEmail,
                AuditAction.SHARE_LINK_CREATED, AuditEntityType.SHARE_LINK, savedEntity.getId(),
                String.format("Created expiring %s share link", savedEntity.getResourceType()),
                Map.of("resourceType", savedEntity.getResourceType().name(), "expiryHours", expiryHours)
        ));
        String shareUrl = buildShareUrl(rawToken);
        String whatsappUrl = buildWhatsappUrl(savedEntity.getResourceType(), propertyEntity.getName(), shareUrl);
        log.info("Share link created. shareLinkId={}, resourceType={}, resourceId={}",
                savedEntity.getId(), savedEntity.getResourceType(), savedEntity.getResourceId());
        return mapToResponse(savedEntity, shareUrl, whatsappUrl);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponseDTO<ShareLinkResponseDTO> listShareLinks(
            String principalEmail,
            UUID propertyId,
            int page,
            Integer size
    ) {
        User actorUser = accessGuardService.loadActiveUser(principalEmail);
        OrganizationEntity organization = accessGuardService.resolveUserOrganization(actorUser);
        List<UUID> accessiblePropertyIds = accessGuardService.resolveAccessiblePropertyIds(
                actorUser, Optional.ofNullable(propertyId)
        );
        Pageable pageable = reportSupport.buildReportPageable(
                page, size, false, Sort.by(Sort.Direction.DESC, "createdAt")
        );
        if (accessiblePropertyIds.isEmpty()) {
            return PageResponseDTO.from(Page.empty(pageable));
        }
        Page<ShareLinkResponseDTO> pageResult = shareLinkRepository
                .findAllByOrganizationIdAndPropertyIdInOrderByCreatedAtDesc(
                        organization.getId(), accessiblePropertyIds, pageable)
                .map(linkEntity -> mapToResponse(linkEntity, null, null));
        return PageResponseDTO.from(pageResult);
    }

    @Override
    @Transactional
    public ShareLinkResponseDTO revokeShareLink(String principalEmail, UUID shareLinkId) {
        User actorUser = accessGuardService.loadActiveUser(principalEmail);
        OrganizationEntity organization = accessGuardService.resolveUserOrganization(actorUser);
        ShareLinkEntity entity = shareLinkRepository.findWithDetailsById(shareLinkId)
                .filter(link -> link.getOrganization().getId().equals(organization.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Share link not found"));

        boolean isCreator = entity.getCreatedByUser().getId().equals(actorUser.getId());
        if (!isCreator && actorUser.getRole() != Role.PG_OWNER && actorUser.getRole() != Role.MANAGER) {
            throw new ForbiddenException("Insufficient permissions to revoke share link");
        }
        Optional.ofNullable(entity.getProperty()).ifPresent(propertyEntity -> {
            if (!isCreator) {
                accessGuardService.requirePropertyManagementAccess(actorUser, propertyEntity.getId(), "READ");
            }
        });
        if (Optional.ofNullable(entity.getRevokedAt()).isEmpty()) {
            entity.setRevokedAt(Instant.now());
            shareLinkRepository.save(entity);
            auditLogService.recordEvent(new AuditLogCommand(
                    organization, entity.getProperty(), actorUser, principalEmail,
                    AuditAction.SHARE_LINK_REVOKED, AuditEntityType.SHARE_LINK, entity.getId(),
                    "Revoked expiring share link",
                    Map.of("resourceType", entity.getResourceType().name())
            ));
        }
        return mapToResponse(entity, null, null);
    }

    @Override
    @Transactional
    public PublicShareViewResponseDTO getPublicShareView(String rawToken) {
        ShareLinkEntity entity = resolveActiveShareLink(rawToken);
        entity.setViewCount(entity.getViewCount() + 1L);
        shareLinkRepository.save(entity);
        return buildPublicShareView(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] exportPublicSharePdf(String rawToken) {
        ShareLinkEntity entity = resolveActiveShareLink(rawToken);
        return pdfExportService.exportPublicSharePdf(buildPublicShareView(entity));
    }

    private ShareLinkEntity resolveActiveShareLink(String rawToken) {
        if (StringUtils.isBlank(rawToken)) {
            throw new ValidationException("Share token is required");
        }
        ShareLinkEntity entity = shareLinkRepository.findByTokenHash(HashUtils.sha256Hex(rawToken.trim()))
                .orElseThrow(() -> new ResourceNotFoundException("Share link not found"));
        if (Optional.ofNullable(entity.getRevokedAt()).isPresent()) {
            throw new ValidationException("Share link has been revoked");
        }
        if (entity.getExpiresAt().isBefore(Instant.now())) {
            throw new ValidationException("Share link has expired");
        }
        return entity;
    }

    private PropertyEntity resolveAndAuthorizeTargetProperty(
            User actorUser,
            String principalEmail,
            CreateShareLinkRequestDTO requestDTO
    ) {
        return switch (requestDTO.resourceType()) {
            case INVOICE -> {
                InvoiceDetailResponseDTO invoice = invoiceService.getInvoiceDetail(principalEmail, requestDTO.resourceId());
                yield loadProperty(invoice.propertyId());
            }
            case PAYMENT_RECEIPT -> {
                PaymentReceiptResponseDTO receipt = paymentService.getPaymentReceipt(principalEmail, requestDTO.resourceId());
                PaymentEntity payment = paymentRepository.findWithDetailsById(receipt.paymentId())
                        .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
                yield payment.getProperty();
            }
            case DEPOSIT_STATEMENT -> {
                depositService.getTenancyDepositSummary(principalEmail, requestDTO.resourceId(), 0, 1);
                TenancyEntity tenancy = tenancyRepository.findWithDetailsById(requestDTO.resourceId())
                        .orElseThrow(() -> new ResourceNotFoundException("Tenancy not found"));
                yield tenancy.getProperty();
            }
            case PUBLIC_VACANCY -> accessGuardService.requirePropertyManagementAccess(
                    actorUser, requestDTO.resourceId(), "READ"
            );
        };
    }

    private PublicShareViewResponseDTO buildPublicShareView(ShareLinkEntity shareLink) {
        return switch (shareLink.getResourceType()) {
            case PUBLIC_VACANCY -> buildPublicVacancyView(shareLink);
            case INVOICE -> buildInvoiceShareView(shareLink);
            case PAYMENT_RECEIPT -> buildPaymentReceiptShareView(shareLink);
            case DEPOSIT_STATEMENT -> buildDepositStatementShareView(shareLink);
        };
    }

    private PublicShareViewResponseDTO buildPublicVacancyView(ShareLinkEntity shareLink) {
        PropertyEntity propertyEntity = propertyRepository.findById(shareLink.getResourceId())
                .orElseThrow(() -> new ResourceNotFoundException("Property not found"));
        Page<BedEntity> vacantBeds = bedRepository.findAvailability(
                List.of(propertyEntity.getId()),
                true, BedStatus.VACANT,
                false, null,
                false, null,
                false, null,
                PageRequest.of(0, PUBLIC_VACANCY_LIMIT, Sort.by(Sort.Direction.ASC, "bedCode"))
        );
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("Property", propertyEntity.getName());
        metadata.put("Location", String.format("%s, %s", propertyEntity.getCity(), propertyEntity.getState()));
        metadata.put("Address", propertyEntity.getAddressLine());
        metadata.put("Contact Phone", StringUtils.defaultIfBlank(propertyEntity.getContactPhone(), "-"));

        List<Map<String, Object>> lineItems = vacantBeds.getContent().stream()
                .map(this::mapPublicVacancyBed)
                .toList();
        return new PublicShareViewResponseDTO(
                ShareResourceType.PUBLIC_VACANCY,
                shareLink.getOrganization().getName(),
                propertyEntity.getName(),
                "Available Beds & Rooms",
                String.format("%s, %s", propertyEntity.getCity(), propertyEntity.getState()),
                shareLink.getExpiresAt(),
                true,
                metadata,
                lineItems,
                Map.of("vacantBeds", BigDecimal.valueOf(lineItems.size()))
        );
    }

    private PublicShareViewResponseDTO buildInvoiceShareView(ShareLinkEntity shareLink) {
        InvoiceEntity invoice = invoiceRepository.findWithDetailsById(shareLink.getResourceId())
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));
        List<InvoiceItemEntity> items = invoiceItemRepository.findAllByInvoiceIdOrderBySortOrderAsc(invoice.getId());
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("Invoice Number", invoice.getInvoiceNumber());
        metadata.put("Tenant", invoice.getTenant().getFullName());
        metadata.put("Due Date", invoice.getDueDate().toString());
        metadata.put("Status", invoice.getInvoiceStatus().name());

        List<Map<String, Object>> rows = items.stream().map(this::mapInvoiceItemRow).toList();
        return new PublicShareViewResponseDTO(
                ShareResourceType.INVOICE,
                shareLink.getOrganization().getName(),
                invoice.getProperty().getName(),
                "Invoice " + invoice.getInvoiceNumber(),
                String.format("Period %d-%02d", invoice.getBillingYear(), invoice.getBillingMonth()),
                shareLink.getExpiresAt(),
                true,
                metadata,
                rows,
                Map.of(
                        "totalAmount", invoice.getTotalAmount(),
                        "paidAmount", invoice.getPaidAmount(),
                        "balanceDue", invoice.getBalanceDue()
                )
        );
    }

    private PublicShareViewResponseDTO buildPaymentReceiptShareView(ShareLinkEntity shareLink) {
        PaymentEntity payment = paymentRepository.findWithDetailsById(shareLink.getResourceId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment receipt not found"));
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("Receipt Number", payment.getReceiptNumber());
        metadata.put("Invoice Number", payment.getInvoice().getInvoiceNumber());
        metadata.put("Tenant", payment.getTenant().getFullName());
        metadata.put("Method", payment.getPaymentMethod().name());
        metadata.put("Status", payment.getPaymentStatus().name());
        metadata.put("Paid At", reportSupport.formatInstantIst(payment.getPaidAt()));
        return new PublicShareViewResponseDTO(
                ShareResourceType.PAYMENT_RECEIPT,
                shareLink.getOrganization().getName(),
                payment.getProperty().getName(),
                "Payment Receipt " + payment.getReceiptNumber(),
                "Official Payment Confirmation",
                shareLink.getExpiresAt(),
                true,
                metadata,
                List.of(),
                Map.of(
                        "amountPaid", payment.getAmount(),
                        "invoiceBalanceDue", payment.getInvoice().getBalanceDue()
                )
        );
    }

    private PublicShareViewResponseDTO buildDepositStatementShareView(ShareLinkEntity shareLink) {
        TenancyEntity tenancy = tenancyRepository.findWithDetailsById(shareLink.getResourceId())
                .orElseThrow(() -> new ResourceNotFoundException("Tenancy not found"));
        List<DepositTransactionEntity> transactions =
                depositTransactionRepository.findAllByTenancyIdOrderByTransactionDateDescCreatedAtDesc(tenancy.getId());
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("Tenant", tenancy.getTenant().getFullName());
        metadata.put("Room / Bed", String.format("R%s / %s", tenancy.getRoom().getNumber(), tenancy.getBed().getBedCode()));
        metadata.put("Refund Status", tenancy.getRefundStatus().name());
        List<Map<String, Object>> rows = transactions.stream().map(this::mapDepositTxRow).toList();
        BigDecimal collected = sumByType(transactions, DepositTransactionType.COLLECTED);
        BigDecimal refunded = sumByType(transactions, DepositTransactionType.REFUND);
        BigDecimal deductions = sumByType(transactions, DepositTransactionType.DEDUCTION);
        BigDecimal heldBalance = collected.subtract(refunded).subtract(deductions);
        return new PublicShareViewResponseDTO(
                ShareResourceType.DEPOSIT_STATEMENT,
                shareLink.getOrganization().getName(),
                tenancy.getProperty().getName(),
                "Security Deposit Statement",
                "Room " + tenancy.getRoom().getNumber() + " / Bed " + tenancy.getBed().getBedCode(),
                shareLink.getExpiresAt(),
                true,
                metadata,
                rows,
                Map.of("collected", collected, "refunded", refunded, "deductions", deductions, "heldBalance", heldBalance)
        );
    }

    private Map<String, Object> mapPublicVacancyBed(BedEntity bedEntity) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("roomNumber", bedEntity.getRoom().getNumber());
        row.put("roomType", bedEntity.getRoom().getRoomType());
        row.put("bedCode", bedEntity.getBedCode());
        row.put("monthlyRent", bedEntity.getMonthlyRent());
        row.put("defaultDeposit", bedEntity.getDefaultDeposit());
        row.put("availableFrom", Optional.ofNullable(bedEntity.getAvailableFrom()).map(Object::toString).orElse("Immediate"));
        return row;
    }

    private Map<String, Object> mapInvoiceItemRow(InvoiceItemEntity itemEntity) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("description", itemEntity.getDescription());
        row.put("quantity", itemEntity.getQuantity());
        row.put("unitAmount", itemEntity.getUnitAmount());
        row.put("lineTotal", itemEntity.getLineTotal());
        return row;
    }

    private Map<String, Object> mapDepositTxRow(DepositTransactionEntity txEntity) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("date", txEntity.getTransactionDate().toString());
        row.put("type", txEntity.getTransactionType().name());
        row.put("method", txEntity.getPaymentMethod().name());
        row.put("amount", txEntity.getAmount());
        row.put("reason", StringUtils.defaultIfBlank(txEntity.getReason(), "-"));
        return row;
    }

    private BigDecimal sumByType(List<DepositTransactionEntity> transactions, DepositTransactionType type) {
        return transactions.stream()
                .filter(txEntity -> txEntity.getTransactionType() == type)
                .map(DepositTransactionEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private PropertyEntity loadProperty(UUID propertyId) {
        return propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Property not found"));
    }

    private int resolveExpiryHours(Integer requestedHours) {
        int resolved = Optional.ofNullable(requestedHours).orElse(appProperties.getShare().getDefaultExpiryHours());
        int maxAllowed = appProperties.getShare().getMaxExpiryHours();
        if (resolved < 1 || resolved > maxAllowed) {
            throw new ValidationException(String.format("Expiry hours must be between 1 and %d", maxAllowed));
        }
        return resolved;
    }

    private ShareLinkResponseDTO mapToResponse(ShareLinkEntity entity, String shareUrl, String whatsappUrl) {
        boolean isActive = Optional.ofNullable(entity.getRevokedAt()).isEmpty()
                && entity.getExpiresAt().isAfter(Instant.now());
        UUID propertyId = Optional.ofNullable(entity.getProperty()).map(PropertyEntity::getId).orElse(null);
        String propertyName = Optional.ofNullable(entity.getProperty()).map(PropertyEntity::getName).orElse(null);
        return new ShareLinkResponseDTO(
                entity.getId(),
                entity.getResourceType(),
                entity.getResourceId(),
                propertyId,
                propertyName,
                shareUrl,
                whatsappUrl,
                entity.getExpiresAt(),
                entity.getRevokedAt(),
                entity.getViewCount(),
                isActive,
                entity.getCreatedAt()
        );
    }

    private String buildShareUrl(String rawToken) {
        String base = StringUtils.removeEnd(appProperties.getShare().getPublicBaseUrl(), "/");
        return String.format("%s/%s", base, rawToken);
    }

    private String buildWhatsappUrl(ShareResourceType resourceType, String propertyName, String shareUrl) {
        String label = switch (resourceType) {
            case INVOICE -> "Rent / Charge Invoice";
            case PAYMENT_RECEIPT -> "Payment Receipt";
            case DEPOSIT_STATEMENT -> "Security Deposit Statement";
            case PUBLIC_VACANCY -> "Available Beds & Vacancy Summary";
        };
        String message = String.format("%s from %s: %s", label, propertyName, shareUrl);
        return "https://wa.me/?text=" + URLEncoder.encode(message, StandardCharsets.UTF_8);
    }
}
