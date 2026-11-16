package com.upcraft.stay.billing.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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

import com.upcraft.stay.audit.dto.AuditLogCommand;
import com.upcraft.stay.audit.enums.AuditAction;
import com.upcraft.stay.audit.enums.AuditEntityType;
import com.upcraft.stay.audit.service.AuditLogService;
import com.upcraft.stay.billing.dto.FinancialSummaryResponseDTO;
import com.upcraft.stay.billing.dto.PaymentReceiptResponseDTO;
import com.upcraft.stay.billing.dto.PaymentResponseDTO;
import com.upcraft.stay.billing.dto.RecordPaymentRequestDTO;
import com.upcraft.stay.billing.dto.ReversePaymentRequestDTO;
import com.upcraft.stay.billing.dto.TenantLedgerResponseDTO;
import com.upcraft.stay.billing.entity.InvoiceEntity;
import com.upcraft.stay.billing.entity.PaymentAdjustmentEntity;
import com.upcraft.stay.billing.entity.PaymentEntity;
import com.upcraft.stay.billing.mapper.BillingMapper;
import com.upcraft.stay.billing.repository.DepositTransactionRepository;
import com.upcraft.stay.billing.repository.InvoiceRepository;
import com.upcraft.stay.billing.repository.PaymentAdjustmentRepository;
import com.upcraft.stay.billing.repository.PaymentRepository;
import com.upcraft.stay.billing.service.GatewayRecordingResult;
import com.upcraft.stay.billing.service.PaymentGatewayBoundary;
import com.upcraft.stay.billing.service.PaymentService;
import com.upcraft.stay.common.config.AppProperties;
import com.upcraft.stay.common.constants.AppConstants;
import com.upcraft.stay.common.dto.PageResponseDTO;
import com.upcraft.stay.common.enums.DepositTransactionType;
import com.upcraft.stay.common.enums.InvoiceStatus;
import com.upcraft.stay.common.enums.PaymentAdjustmentType;
import com.upcraft.stay.common.enums.PaymentMethod;
import com.upcraft.stay.common.enums.PaymentStatus;
import com.upcraft.stay.common.exception.ConflictException;
import com.upcraft.stay.common.exception.ResourceNotFoundException;
import com.upcraft.stay.common.exception.ValidationException;
import com.upcraft.stay.common.util.PaginationUtils;
import com.upcraft.stay.domain.entity.User;
import com.upcraft.stay.security.AccessGuardService;
import com.upcraft.stay.tenant.entity.TenantEntity;
import com.upcraft.stay.tenant.repository.TenantRepository;

/**
 * Transactional payment recording, reversal adjustments, receipts, summaries, and tenant ledger service.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private static final ZoneId INDIA_ZONE = ZoneId.of(AppConstants.DEFAULT_TIMEZONE);
    private static final Set<String> SORT_FIELDS = Set.of("paidAt", "amount", "createdAt");
    private static final List<InvoiceStatus> EXCLUDED_REVENUE_STATUSES = List.of(
            InvoiceStatus.CANCELLED, InvoiceStatus.WAIVED
    );
    private static final List<InvoiceStatus> OPEN_INVOICE_STATUSES = List.of(
            InvoiceStatus.UNPAID, InvoiceStatus.PARTIALLY_PAID, InvoiceStatus.OVERDUE
    );

    private final AccessGuardService accessGuardService;
    private final BillingAccessHelper billingAccessHelper;
    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentAdjustmentRepository paymentAdjustmentRepository;
    private final DepositTransactionRepository depositTransactionRepository;
    private final TenantRepository tenantRepository;
    private final PaymentGatewayBoundary paymentGatewayBoundary;
    private final InvoiceGenerationHelper generationHelper;
    private final PaymentSettlementHelper settlementHelper;
    private final TenantLedgerBuilder tenantLedgerBuilder;
    private final BillingMapper billingMapper;
    private final AppProperties appProperties;
    private final AuditLogService auditLogService;

    @Override
    @Transactional(readOnly = true)
    public PageResponseDTO<PaymentResponseDTO> listPayments(
            String principalEmail,
            UUID propertyId,
            PaymentStatus paymentStatus,
            PaymentMethod paymentMethod,
            UUID invoiceId,
            UUID tenantId,
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
                page, size, sortBy, sortDirection, SORT_FIELDS, "paidAt", appProperties
        );
        if (CollectionUtils.isEmpty(propertyIds)) {
            return PageResponseDTO.from(Page.empty(pageable));
        }

        Optional<PaymentStatus> statusOpt = Optional.ofNullable(paymentStatus);
        Optional<PaymentMethod> methodOpt = Optional.ofNullable(paymentMethod);
        Optional<UUID> invoiceOpt = Optional.ofNullable(invoiceId);
        Optional<UUID> tenantOpt = Optional.ofNullable(tenantId);

        Page<PaymentResponseDTO> result = paymentRepository.findAccessiblePayments(
                        propertyIds,
                        statusOpt.isPresent(), statusOpt.orElse(PaymentStatus.COMPLETED),
                        methodOpt.isPresent(), methodOpt.orElse(PaymentMethod.CASH),
                        invoiceOpt.isPresent(), invoiceOpt.orElseGet(UUID::randomUUID),
                        tenantOpt.isPresent(), tenantOpt.orElseGet(UUID::randomUUID),
                        pageable
                )
                .map(billingMapper::toPaymentResponseDTO);
        return PageResponseDTO.from(result);
    }

    @Override
    @Transactional
    public PaymentResponseDTO recordPayment(
            String principalEmail,
            UUID invoiceId,
            RecordPaymentRequestDTO recordPaymentRequestDTO
    ) {
        User currentUser = accessGuardService.loadActiveUser(principalEmail);
        InvoiceEntity invoice = invoiceRepository.findLockedById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));
        accessGuardService.requirePropertyManagementAccess(currentUser, invoice.getProperty().getId(), "PAYMENTS");

        String trimmedIdempotency = StringUtils.trimToNull(recordPaymentRequestDTO.idempotencyKey());
        if (StringUtils.isNotBlank(trimmedIdempotency)) {
            Optional<PaymentEntity> existingPayment =
                    paymentRepository.findByInvoiceIdAndIdempotencyKey(invoiceId, trimmedIdempotency);
            if (existingPayment.isPresent()) {
                return billingMapper.toPaymentResponseDTO(existingPayment.get());
            }
        }

        validatePayableInvoice(invoice, recordPaymentRequestDTO.amount());
        GatewayRecordingResult gatewayResult = paymentGatewayBoundary.verifyAndResolve(recordPaymentRequestDTO);

        PaymentEntity payment = buildPaymentEntity(
                invoice, currentUser, recordPaymentRequestDTO, trimmedIdempotency, gatewayResult
        );
        PaymentEntity savedPayment = paymentRepository.save(payment);

        List<PaymentEntity> invoicePayments = paymentRepository.findAllByInvoiceIdOrderByPaidAtDescCreatedAtDesc(invoiceId);
        settlementHelper.recomputeInvoiceFromPayments(invoice, invoicePayments, generationHelper);
        invoiceRepository.save(invoice);
        auditLogService.recordEvent(new AuditLogCommand(
                invoice.getOrganization(), invoice.getProperty(), currentUser, principalEmail,
                AuditAction.PAYMENT_RECORDED, AuditEntityType.PAYMENT, savedPayment.getId(),
                String.format("Recorded %s payment %s for invoice %s", savedPayment.getPaymentMethod(), savedPayment.getReceiptNumber(), invoice.getInvoiceNumber()),
                Map.of("amount", savedPayment.getAmount(), "method", savedPayment.getPaymentMethod().name(), "invoiceNumber", invoice.getInvoiceNumber())
        ));

        log.info("Payment recorded. paymentId={}, invoiceId={}, amount={}",
                savedPayment.getId(), invoiceId, savedPayment.getAmount());
        return billingMapper.toPaymentResponseDTO(savedPayment);
    }

    @Override
    @Transactional
    public PaymentResponseDTO reversePayment(
            String principalEmail,
            UUID paymentId,
            ReversePaymentRequestDTO reversePaymentRequestDTO
    ) {
        User currentUser = accessGuardService.loadActiveUser(principalEmail);
        PaymentEntity payment = paymentRepository.findWithDetailsById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
        accessGuardService.requirePropertyManagementAccess(currentUser, payment.getProperty().getId(), "PAYMENTS");

        if (payment.getPaymentStatus() != PaymentStatus.COMPLETED) {
            throw new ConflictException("Only completed payments can be reversed");
        }
        InvoiceEntity invoice = invoiceRepository.findLockedById(payment.getInvoice().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));

        payment.setPaymentStatus(PaymentStatus.REVERSED);
        PaymentEntity updatedPayment = paymentRepository.save(payment);

        PaymentAdjustmentEntity adjustment = new PaymentAdjustmentEntity();
        adjustment.setOrganization(payment.getOrganization());
        adjustment.setPayment(updatedPayment);
        adjustment.setInvoice(invoice);
        adjustment.setAdjustmentType(PaymentAdjustmentType.REVERSAL);
        adjustment.setAmount(payment.getAmount());
        adjustment.setReason(reversePaymentRequestDTO.reason().trim());
        adjustment.setAdjustedByUser(currentUser);
        adjustment.setAdjustedAt(Instant.now());
        paymentAdjustmentRepository.save(adjustment);

        List<PaymentEntity> invoicePayments =
                paymentRepository.findAllByInvoiceIdOrderByPaidAtDescCreatedAtDesc(invoice.getId());
        settlementHelper.recomputeInvoiceFromPayments(invoice, invoicePayments, generationHelper);
        invoiceRepository.save(invoice);
        auditLogService.recordEvent(new AuditLogCommand(
                payment.getOrganization(), payment.getProperty(), currentUser, principalEmail,
                AuditAction.PAYMENT_REVERSED, AuditEntityType.PAYMENT, updatedPayment.getId(),
                String.format("Reversed payment %s on invoice %s", updatedPayment.getReceiptNumber(), invoice.getInvoiceNumber()),
                Map.of("amount", updatedPayment.getAmount(), "reason", reversePaymentRequestDTO.reason().trim())
        ));

        log.info("Payment reversed. paymentId={}, invoiceId={}", paymentId, invoice.getId());
        return billingMapper.toPaymentResponseDTO(updatedPayment);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentReceiptResponseDTO getPaymentReceipt(String principalEmail, UUID paymentId) {
        User currentUser = accessGuardService.loadActiveUser(principalEmail);
        PaymentEntity payment = paymentRepository.findWithDetailsById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment receipt not found"));
        billingAccessHelper.requireTenantReadAccess(currentUser, payment.getTenant());

        InvoiceEntity invoice = payment.getInvoice();
        return new PaymentReceiptResponseDTO(
                payment.getId(), payment.getReceiptNumber(),
                payment.getOrganization().getName(),
                payment.getProperty().getName(),
                String.format("%s, %s, %s - %s",
                        payment.getProperty().getAddressLine(), payment.getProperty().getCity(),
                        payment.getProperty().getState(), payment.getProperty().getPinCode()),
                payment.getTenant().getFullName(), payment.getTenant().getPhone(),
                Optional.ofNullable(invoice.getRoom()).map(room -> room.getNumber()).orElse("—"),
                Optional.ofNullable(invoice.getBed()).map(bed -> bed.getBedCode()).orElse("—"),
                invoice.getInvoiceNumber(), invoice.getBillingYear(), invoice.getBillingMonth(),
                payment.getPaidAt(), payment.getPaymentMethod(), payment.getPaymentStatus(),
                payment.getReferenceNumber(), payment.getAmount(),
                invoice.getTotalAmount(), invoice.getPaidAmount(), invoice.getBalanceDue(),
                payment.getRecordedByUser().getName(), payment.getNotes()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public FinancialSummaryResponseDTO getFinancialSummary(
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
            return new FinancialSummaryResponseDTO(
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0L, 0.0,
                    BigDecimal.ZERO, BigDecimal.ZERO
            );
        }

        Optional<Integer> yearOpt = Optional.ofNullable(billingYear);
        Optional<Integer> monthOpt = Optional.ofNullable(billingMonth);
        LocalDate todayIndia = LocalDate.now(INDIA_ZONE);

        BigDecimal totalBilled = invoiceRepository.sumActiveBilledAmount(
                propertyIds, EXCLUDED_REVENUE_STATUSES, yearOpt.isPresent(), yearOpt.orElse(0),
                monthOpt.isPresent(), monthOpt.orElse(0));
        BigDecimal totalCollected = invoiceRepository.sumActiveCollectedAmount(
                propertyIds, EXCLUDED_REVENUE_STATUSES, yearOpt.isPresent(), yearOpt.orElse(0),
                monthOpt.isPresent(), monthOpt.orElse(0));
        BigDecimal totalOutstanding = invoiceRepository.sumOutstandingBalance(
                propertyIds, OPEN_INVOICE_STATUSES, yearOpt.isPresent(), yearOpt.orElse(0),
                monthOpt.isPresent(), monthOpt.orElse(0));
        BigDecimal overdueBalance = invoiceRepository.sumOverdueBalance(
                propertyIds, OPEN_INVOICE_STATUSES, todayIndia, yearOpt.isPresent(), yearOpt.orElse(0),
                monthOpt.isPresent(), monthOpt.orElse(0));
        long overdueCount = invoiceRepository.countOverdueInvoices(propertyIds, OPEN_INVOICE_STATUSES, todayIndia);

        BigDecimal depositCollected = depositTransactionRepository
                .sumByPropertyIdsAndType(propertyIds, DepositTransactionType.COLLECTED)
                .add(depositTransactionRepository.sumByPropertyIdsAndType(propertyIds, DepositTransactionType.ADJUSTMENT));
        BigDecimal depositOutflows = depositTransactionRepository
                .sumByPropertyIdsAndType(propertyIds, DepositTransactionType.DEDUCTION)
                .add(depositTransactionRepository.sumByPropertyIdsAndType(propertyIds, DepositTransactionType.FORFEIT));
        BigDecimal depositRefunded = depositTransactionRepository
                .sumByPropertyIdsAndType(propertyIds, DepositTransactionType.REFUND);
        BigDecimal depositHeld = depositCollected.subtract(depositOutflows).subtract(depositRefunded).max(BigDecimal.ZERO);

        double collectionRate = totalBilled.compareTo(BigDecimal.ZERO) > 0
                ? totalCollected.multiply(BigDecimal.valueOf(100))
                        .divide(totalBilled, 2, RoundingMode.HALF_UP).doubleValue()
                : 0.0;

        return new FinancialSummaryResponseDTO(
                totalBilled, totalCollected, totalOutstanding, overdueBalance,
                overdueCount, collectionRate, depositHeld, depositRefunded
        );
    }

    @Override
    @Transactional(readOnly = true)
    public TenantLedgerResponseDTO getTenantLedger(String principalEmail, UUID tenantId, int page, Integer size) {
        User currentUser = accessGuardService.loadActiveUser(principalEmail);
        TenantEntity tenant = tenantRepository.findWithDetailsById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
        billingAccessHelper.requireTenantReadAccess(currentUser, tenant);

        Pageable pageable = PaginationUtils.createPageable(
                page, size, "entryDate", "DESC", Set.of("entryDate"), "entryDate", appProperties
        );
        return tenantLedgerBuilder.build(
                tenant,
                invoiceRepository.findAllByTenantIdOrderByBillingYearDescBillingMonthDescIssueDateDesc(tenantId),
                paymentRepository.findAllByTenantIdOrderByPaidAtDescCreatedAtDesc(tenantId),
                paymentAdjustmentRepository.findAllByPaymentTenantIdOrderByAdjustedAtDesc(tenantId),
                depositTransactionRepository.findAllByTenantIdOrderByTransactionDateDescCreatedAtDesc(tenantId),
                pageable
        );
    }

    private void validatePayableInvoice(InvoiceEntity invoice, BigDecimal paymentAmount) {
        if (invoice.getInvoiceStatus() == InvoiceStatus.CANCELLED
                || invoice.getInvoiceStatus() == InvoiceStatus.WAIVED) {
            throw new ConflictException("Cannot record payment on a cancelled or waived invoice");
        }
        if (invoice.getBalanceDue().compareTo(BigDecimal.ZERO) <= 0
                || invoice.getInvoiceStatus() == InvoiceStatus.PAID) {
            throw new ConflictException("Invoice is already fully paid");
        }
        BigDecimal normalizedAmount = paymentAmount.setScale(2, RoundingMode.HALF_UP);
        if (normalizedAmount.compareTo(invoice.getBalanceDue()) > 0) {
            throw new ValidationException("Overpayment is not allowed; payment exceeds invoice balance due");
        }
    }

    private PaymentEntity buildPaymentEntity(
            InvoiceEntity invoice,
            User recorderUser,
            RecordPaymentRequestDTO requestDTO,
            String trimmedIdempotency,
            GatewayRecordingResult gatewayResult
    ) {
        long seq = paymentRepository.countByOrganizationId(invoice.getOrganization().getId()) + 1;
        PaymentEntity payment = new PaymentEntity();
        payment.setOrganization(invoice.getOrganization());
        payment.setProperty(invoice.getProperty());
        payment.setTenant(invoice.getTenant());
        payment.setInvoice(invoice);
        payment.setReceiptNumber(settlementHelper.nextReceiptNumber(seq));
        payment.setAmount(requestDTO.amount().setScale(2, RoundingMode.HALF_UP));
        payment.setPaidAt(Optional.ofNullable(requestDTO.paidAt()).orElseGet(Instant::now));
        payment.setPaymentMethod(requestDTO.paymentMethod());
        payment.setPaymentStatus(gatewayResult.paymentStatus());
        payment.setReferenceNumber(StringUtils.trimToNull(requestDTO.referenceNumber()));
        payment.setIdempotencyKey(trimmedIdempotency);
        payment.setRecordedByUser(recorderUser);
        payment.setGatewayProvider(gatewayResult.providerName());
        payment.setGatewayPaymentId(gatewayResult.gatewayPaymentId());
        payment.setProofUrl(StringUtils.trimToNull(requestDTO.proofUrl()));
        payment.setNotes(StringUtils.trimToNull(requestDTO.notes()));
        return payment;
    }
}

