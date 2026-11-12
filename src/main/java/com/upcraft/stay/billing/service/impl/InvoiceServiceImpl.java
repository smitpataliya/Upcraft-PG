package com.upcraft.stay.billing.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.upcraft.stay.billing.dto.CreateCustomInvoiceRequestDTO;
import com.upcraft.stay.billing.dto.CustomInvoiceLineItemRequestDTO;
import com.upcraft.stay.billing.dto.GenerateInvoicesRequestDTO;
import com.upcraft.stay.billing.dto.GenerateInvoicesResultResponseDTO;
import com.upcraft.stay.billing.dto.InvoiceDetailResponseDTO;
import com.upcraft.stay.billing.dto.InvoiceSummaryResponseDTO;
import com.upcraft.stay.billing.dto.UpdateInvoiceStatusRequestDTO;
import com.upcraft.stay.billing.entity.InvoiceEntity;
import com.upcraft.stay.billing.entity.InvoiceItemEntity;
import com.upcraft.stay.billing.mapper.BillingMapper;
import com.upcraft.stay.billing.repository.InvoiceItemRepository;
import com.upcraft.stay.billing.repository.InvoiceRepository;
import com.upcraft.stay.billing.repository.PaymentAdjustmentRepository;
import com.upcraft.stay.billing.repository.PaymentRepository;
import com.upcraft.stay.billing.service.InvoiceService;
import com.upcraft.stay.common.config.AppProperties;
import com.upcraft.stay.common.constants.AppConstants;
import com.upcraft.stay.common.dto.PageResponseDTO;
import com.upcraft.stay.common.enums.InvoiceItemType;
import com.upcraft.stay.common.enums.InvoiceStatus;
import com.upcraft.stay.common.enums.InvoiceType;
import com.upcraft.stay.common.enums.TenancyStatus;
import com.upcraft.stay.common.exception.ConflictException;
import com.upcraft.stay.common.exception.ResourceNotFoundException;
import com.upcraft.stay.common.exception.ValidationException;
import com.upcraft.stay.common.util.PaginationUtils;
import com.upcraft.stay.domain.entity.User;
import com.upcraft.stay.organization.entity.OrganizationEntity;
import com.upcraft.stay.security.AccessGuardService;
import com.upcraft.stay.tenant.entity.TenancyEntity;
import com.upcraft.stay.tenant.repository.TenancyRepository;

/**
 * Idempotent monthly rent invoice generator and invoice lifecycle service.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceServiceImpl implements InvoiceService {

    private static final ZoneId INDIA_ZONE = ZoneId.of(AppConstants.DEFAULT_TIMEZONE);
    private static final Set<String> SORT_FIELDS = Set.of("dueDate", "issueDate", "totalAmount", "createdAt", "balanceDue");
    private static final List<TenancyStatus> BILLABLE_TENANCY_STATUSES = List.of(
            TenancyStatus.ACTIVE,
            TenancyStatus.NOTICE_PERIOD,
            TenancyStatus.COMPLETED
    );

    private final AccessGuardService accessGuardService;
    private final BillingAccessHelper billingAccessHelper;
    private final TenancyRepository tenancyRepository;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceItemRepository invoiceItemRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentAdjustmentRepository paymentAdjustmentRepository;
    private final InvoiceGenerationHelper generationHelper;
    private final BillingMapper billingMapper;
    private final AppProperties appProperties;

    @Override
    @Transactional
    public GenerateInvoicesResultResponseDTO generateMonthlyRentInvoices(
            String principalEmail,
            GenerateInvoicesRequestDTO generateRequestDTO
    ) {
        User currentUser = accessGuardService.loadActiveUser(principalEmail);
        List<UUID> propertyIds = accessGuardService.resolveAccessiblePropertyIds(
                currentUser, Optional.ofNullable(generateRequestDTO.propertyId())
        );
        propertyIds.forEach(propId -> accessGuardService.requirePropertyManagementAccess(currentUser, propId, "PAYMENTS"));

        YearMonth billingPeriod = YearMonth.of(generateRequestDTO.billingYear(), generateRequestDTO.billingMonth());
        if (CollectionUtils.isEmpty(propertyIds)) {
            return new GenerateInvoicesResultResponseDTO(
                    billingPeriod.getYear(), billingPeriod.getMonthValue(), 0, 0, List.of()
            );
        }

        List<TenancyEntity> billableTenancies = tenancyRepository.findBillableTenanciesForPeriod(
                propertyIds, BILLABLE_TENANCY_STATUSES, billingPeriod.atDay(1), billingPeriod.atEndOfMonth()
        );
        if (CollectionUtils.isEmpty(billableTenancies)) {
            return new GenerateInvoicesResultResponseDTO(
                    billingPeriod.getYear(), billingPeriod.getMonthValue(), 0, 0, List.of()
            );
        }

        List<UUID> tenancyIds = billableTenancies.stream().map(TenancyEntity::getId).toList();
        Set<UUID> alreadyBilled = new HashSet<>(invoiceRepository.findAlreadyBilledTenancyIds(
                tenancyIds, billingPeriod.getYear(), billingPeriod.getMonthValue(),
                InvoiceType.MONTHLY_RENT, InvoiceStatus.CANCELLED
        ));

        return persistBatchForUnbilledTenancies(billableTenancies, alreadyBilled, billingPeriod, generateRequestDTO);
    }

    @Override
    @Transactional
    public InvoiceDetailResponseDTO createCustomInvoice(
            String principalEmail,
            CreateCustomInvoiceRequestDTO customRequestDTO
    ) {
        User currentUser = accessGuardService.loadActiveUser(principalEmail);
        TenancyEntity tenancy = tenancyRepository.findWithDetailsById(customRequestDTO.tenancyId())
                .orElseThrow(() -> new ResourceNotFoundException("Tenancy record not found"));
        accessGuardService.requirePropertyManagementAccess(currentUser, tenancy.getProperty().getId(), "PAYMENTS");

        LocalDate todayIndia = LocalDate.now(INDIA_ZONE);
        YearMonth billingPeriod = YearMonth.of(
                Optional.ofNullable(customRequestDTO.billingYear()).orElse(todayIndia.getYear()),
                Optional.ofNullable(customRequestDTO.billingMonth()).orElse(todayIndia.getMonthValue())
        );
        long sequence = invoiceRepository.countByOrganizationId(tenancy.getOrganization().getId()) + 1;

        InvoiceEntity invoice = initializeInvoice(
                tenancy, billingPeriod, InvoiceType.CUSTOM,
                Optional.ofNullable(customRequestDTO.dueDate()).orElse(todayIndia.plusDays(7)),
                todayIndia, generationHelper.nextInvoiceNumber(billingPeriod, sequence), customRequestDTO.notes()
        );
        List<InvoiceItemEntity> items = buildCustomItems(invoice, customRequestDTO.items());
        generationHelper.recalculateTotals(invoice, items, todayIndia);

        InvoiceEntity savedInvoice = invoiceRepository.save(invoice);
        items.forEach(item -> item.setInvoice(savedInvoice));
        invoiceItemRepository.saveAll(items);
        log.info("Custom invoice created. invoiceId={}, tenancyId={}", savedInvoice.getId(), tenancy.getId());
        return assembleDetail(savedInvoice, items);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponseDTO<InvoiceSummaryResponseDTO> listInvoices(
            String principalEmail,
            UUID propertyId,
            InvoiceStatus invoiceStatus,
            UUID tenantId,
            Integer billingYear,
            Integer billingMonth,
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
                page, size, sortBy, sortDirection, SORT_FIELDS, "dueDate", appProperties
        );
        if (CollectionUtils.isEmpty(propertyIds)) {
            return PageResponseDTO.from(Page.empty(pageable));
        }

        Optional<InvoiceStatus> statusOpt = Optional.ofNullable(invoiceStatus);
        Optional<UUID> tenantOpt = Optional.ofNullable(tenantId);
        Optional<Integer> yearOpt = Optional.ofNullable(billingYear);
        Optional<Integer> monthOpt = Optional.ofNullable(billingMonth);
        String trimmedSearch = StringUtils.trimToEmpty(search);

        Page<InvoiceSummaryResponseDTO> result = invoiceRepository.findAccessibleInvoices(
                        propertyIds,
                        statusOpt.isPresent(), statusOpt.orElse(InvoiceStatus.UNPAID),
                        tenantOpt.isPresent(), tenantOpt.orElseGet(UUID::randomUUID),
                        yearOpt.isPresent(), yearOpt.orElse(0),
                        monthOpt.isPresent(), monthOpt.orElse(0),
                        StringUtils.isNotBlank(trimmedSearch), "%" + trimmedSearch.toLowerCase(Locale.ROOT) + "%",
                        pageable
                )
                .map(billingMapper::toInvoiceSummaryResponseDTO);
        return PageResponseDTO.from(result);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponseDTO<InvoiceSummaryResponseDTO> listMyInvoices(
            String principalEmail,
            int page,
            Integer size,
            String sortBy,
            String sortDirection
    ) {
        User residentUser = accessGuardService.loadActiveUser(principalEmail);
        OrganizationEntity organization = accessGuardService.resolveUserOrganization(residentUser);
        Pageable pageable = PaginationUtils.createPageable(
                page, size, sortBy, sortDirection, SORT_FIELDS, "dueDate", appProperties
        );
        Page<InvoiceSummaryResponseDTO> result = invoiceRepository
                .findSelfInvoices(residentUser.getId(), organization.getId(),
                        residentUser.getEmail().trim().toLowerCase(Locale.ROOT), pageable)
                .map(billingMapper::toInvoiceSummaryResponseDTO);
        return PageResponseDTO.from(result);
    }

    @Override
    @Transactional(readOnly = true)
    public InvoiceDetailResponseDTO getInvoiceDetail(String principalEmail, UUID invoiceId) {
        User currentUser = accessGuardService.loadActiveUser(principalEmail);
        InvoiceEntity invoice = invoiceRepository.findWithDetailsById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));
        billingAccessHelper.requireTenantReadAccess(currentUser, invoice.getTenant());
        List<InvoiceItemEntity> items = invoiceItemRepository.findAllByInvoiceIdOrderBySortOrderAsc(invoiceId);
        return assembleDetail(invoice, items);
    }

    @Override
    @Transactional
    public InvoiceDetailResponseDTO updateInvoiceStatus(
            String principalEmail,
            UUID invoiceId,
            UpdateInvoiceStatusRequestDTO updateStatusRequestDTO
    ) {
        User currentUser = accessGuardService.loadActiveUser(principalEmail);
        InvoiceEntity invoice = invoiceRepository.findLockedById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));
        accessGuardService.requirePropertyManagementAccess(currentUser, invoice.getProperty().getId(), "PAYMENTS");

        InvoiceStatus targetStatus = updateStatusRequestDTO.invoiceStatus();
        if (targetStatus != InvoiceStatus.CANCELLED && targetStatus != InvoiceStatus.WAIVED) {
            throw new ValidationException("Manual status transition supports only CANCELLED or WAIVED");
        }
        if (invoice.getPaidAmount().compareTo(BigDecimal.ZERO) > 0) {
            throw new ConflictException("Reverse recorded payments before cancelling or waiving this invoice");
        }

        invoice.setInvoiceStatus(targetStatus);
        invoice.setBalanceDue(BigDecimal.ZERO);
        invoice.setStatusReason(updateStatusRequestDTO.reason().trim());
        InvoiceEntity saved = invoiceRepository.save(invoice);
        List<InvoiceItemEntity> items = invoiceItemRepository.findAllByInvoiceIdOrderBySortOrderAsc(invoiceId);
        return assembleDetail(saved, items);
    }

    @Override
    @Transactional
    public InvoiceDetailResponseDTO applyLateFee(String principalEmail, UUID invoiceId) {
        User currentUser = accessGuardService.loadActiveUser(principalEmail);
        InvoiceEntity invoice = invoiceRepository.findLockedById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));
        accessGuardService.requirePropertyManagementAccess(currentUser, invoice.getProperty().getId(), "PAYMENTS");

        if (invoice.getInvoiceStatus() == InvoiceStatus.CANCELLED
                || invoice.getInvoiceStatus() == InvoiceStatus.WAIVED
                || invoice.getInvoiceStatus() == InvoiceStatus.PAID) {
            throw new ConflictException("Cannot apply late fee to a settled, cancelled, or waived invoice");
        }
        if (invoice.getLateFeeAmount().compareTo(BigDecimal.ZERO) > 0) {
            throw new ConflictException("Late fee has already been applied to this invoice");
        }

        LocalDate todayIndia = LocalDate.now(INDIA_ZONE);
        BigDecimal lateFee = generationHelper.calculateLateFee(invoice.getProperty(), invoice, todayIndia);
        if (lateFee.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Property late-fee policy or grace period does not trigger a fee yet");
        }

        List<InvoiceItemEntity> items = invoiceItemRepository.findAllByInvoiceIdOrderBySortOrderAsc(invoiceId);
        InvoiceItemEntity feeItem = createLineItem(
                invoice, InvoiceItemType.LATE_FEE, "Late payment fee", BigDecimal.ONE, lateFee, items.size()
        );
        invoiceItemRepository.save(feeItem);
        items.add(feeItem);

        generationHelper.recalculateTotals(invoice, items, todayIndia);
        InvoiceEntity saved = invoiceRepository.save(invoice);
        return assembleDetail(saved, items);
    }

    @Override
    @Transactional
    public InvoiceEntity createElectricityInvoice(
            TenancyEntity tenancy,
            YearMonth billingPeriod,
            LocalDate dueDate,
            String meterNumber,
            BigDecimal unitsAllocated,
            BigDecimal ratePerUnitSnapshot,
            BigDecimal energyCharge,
            BigDecimal fixedAndAdditionalShare,
            BigDecimal taxAmountShare,
            BigDecimal creditShare
    ) {
        LocalDate todayIndia = LocalDate.now(INDIA_ZONE);
        long sequence = invoiceRepository.countByOrganizationId(tenancy.getOrganization().getId()) + 1;
        String notes = String.format("Electricity bill for meter %s (%d-%02d)",
                meterNumber, billingPeriod.getYear(), billingPeriod.getMonthValue());
        InvoiceEntity invoice = initializeInvoice(
                tenancy, billingPeriod, InvoiceType.ELECTRICITY,
                Optional.ofNullable(dueDate).orElse(todayIndia.plusDays(appProperties.getElectricity().getDefaultInvoiceDueDays())),
                todayIndia, generationHelper.nextInvoiceNumber(billingPeriod, sequence), notes
        );

        List<InvoiceItemEntity> items = buildElectricityItems(
                invoice, meterNumber, unitsAllocated, ratePerUnitSnapshot,
                energyCharge, fixedAndAdditionalShare, taxAmountShare, creditShare
        );
        generationHelper.recalculateTotals(invoice, items, todayIndia);

        InvoiceEntity savedInvoice = invoiceRepository.save(invoice);
        items.forEach(item -> item.setInvoice(savedInvoice));
        invoiceItemRepository.saveAll(items);
        return savedInvoice;
    }

    private GenerateInvoicesResultResponseDTO persistBatchForUnbilledTenancies(
            List<TenancyEntity> billableTenancies,
            Set<UUID> alreadyBilled,
            YearMonth billingPeriod,
            GenerateInvoicesRequestDTO generateRequestDTO
    ) {
        LocalDate todayIndia = LocalDate.now(INDIA_ZONE);
        boolean prorate = Boolean.TRUE.equals(generateRequestDTO.prorateMidMonth());
        BigDecimal discount = Optional.ofNullable(generateRequestDTO.defaultDiscountAmount())
                .orElse(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        AtomicLong seqCounter = new AtomicLong(
                invoiceRepository.countByOrganizationId(billableTenancies.get(0).getOrganization().getId())
        );

        List<InvoiceEntity> newInvoices = new ArrayList<>();
        List<InvoiceItemEntity> newItems = new ArrayList<>();

        billableTenancies.stream()
                .filter(tenancy -> !alreadyBilled.contains(tenancy.getId()))
                .forEach(tenancy -> buildMonthlyRentInvoice(
                        tenancy, billingPeriod, todayIndia, prorate, discount,
                        generateRequestDTO.notes(), seqCounter.incrementAndGet(), newInvoices, newItems
                ));

        List<InvoiceEntity> savedInvoices = CollectionUtils.isEmpty(newInvoices)
                ? List.of()
                : invoiceRepository.saveAll(newInvoices);
        Map<String, InvoiceEntity> savedByNumber = savedInvoices.stream()
                .collect(Collectors.toMap(InvoiceEntity::getInvoiceNumber, Function.identity()));
        if (CollectionUtils.isNotEmpty(newItems)) {
            newItems.forEach(item -> item.setInvoice(savedByNumber.get(item.getInvoice().getInvoiceNumber())));
            invoiceItemRepository.saveAll(newItems);
        }

        List<InvoiceSummaryResponseDTO> createdSummaries = savedInvoices.stream()
                .map(billingMapper::toInvoiceSummaryResponseDTO)
                .toList();

        int skippedCount = billableTenancies.size() - newInvoices.size();
        log.info("Monthly rent invoices processed. period={}-{}, generated={}, skipped={}",
                billingPeriod.getYear(), billingPeriod.getMonthValue(), createdSummaries.size(), skippedCount);
        return new GenerateInvoicesResultResponseDTO(
                billingPeriod.getYear(), billingPeriod.getMonthValue(),
                createdSummaries.size(), skippedCount, createdSummaries
        );
    }

    private void buildMonthlyRentInvoice(
            TenancyEntity tenancy,
            YearMonth billingPeriod,
            LocalDate todayIndia,
            boolean prorate,
            BigDecimal discount,
            String notes,
            long sequence,
            List<InvoiceEntity> invoiceSink,
            List<InvoiceItemEntity> itemSink
    ) {
        BigDecimal rentAmount = generationHelper.computeMonthlyRentAmount(tenancy, billingPeriod, prorate);
        if (rentAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        InvoiceEntity invoice = initializeInvoice(
                tenancy, billingPeriod, InvoiceType.MONTHLY_RENT,
                generationHelper.resolvePeriodDueDate(tenancy, billingPeriod),
                todayIndia, generationHelper.nextInvoiceNumber(billingPeriod, sequence), notes
        );
        List<InvoiceItemEntity> itemsForThis = new ArrayList<>();
        itemsForThis.add(createLineItem(
                invoice,
                rentAmount.compareTo(tenancy.getAgreedMonthlyRent()) < 0 ? InvoiceItemType.PRORATION : InvoiceItemType.RENT,
                String.format("Monthly rent — Room %s (%s) %d-%02d",
                        tenancy.getRoom().getNumber(), tenancy.getBed().getBedCode(),
                        billingPeriod.getYear(), billingPeriod.getMonthValue()),
                BigDecimal.ONE, rentAmount, 0
        ));
        if (discount.compareTo(BigDecimal.ZERO) > 0) {
            itemsForThis.add(createLineItem(
                    invoice, InvoiceItemType.DISCOUNT, "Configured monthly discount", BigDecimal.ONE, discount, 1
            ));
        }
        generationHelper.recalculateTotals(invoice, itemsForThis, todayIndia);
        invoiceSink.add(invoice);
        itemSink.addAll(itemsForThis);
    }

    private InvoiceEntity initializeInvoice(
            TenancyEntity tenancy,
            YearMonth billingPeriod,
            InvoiceType type,
            LocalDate dueDate,
            LocalDate issueDate,
            String invoiceNumber,
            String notes
    ) {
        InvoiceEntity invoice = new InvoiceEntity();
        invoice.setOrganization(tenancy.getOrganization());
        invoice.setProperty(tenancy.getProperty());
        invoice.setTenant(tenancy.getTenant());
        invoice.setTenancy(tenancy);
        invoice.setRoom(tenancy.getRoom());
        invoice.setBed(tenancy.getBed());
        invoice.setInvoiceNumber(invoiceNumber);
        invoice.setInvoiceType(type);
        invoice.setBillingYear(billingPeriod.getYear());
        invoice.setBillingMonth(billingPeriod.getMonthValue());
        invoice.setPeriodStart(billingPeriod.atDay(1));
        invoice.setPeriodEnd(billingPeriod.atEndOfMonth());
        invoice.setIssueDate(issueDate);
        invoice.setDueDate(dueDate);
        invoice.setNotes(StringUtils.trimToNull(notes));
        return invoice;
    }

    private List<InvoiceItemEntity> buildCustomItems(
            InvoiceEntity invoice,
            List<CustomInvoiceLineItemRequestDTO> lineRequests
    ) {
        AtomicInteger order = new AtomicInteger(0);
        return lineRequests.stream()
                .map(line -> {
                    BigDecimal qty = Optional.ofNullable(line.quantity()).orElse(BigDecimal.ONE);
                    BigDecimal total = qty.multiply(line.unitAmount()).setScale(2, RoundingMode.HALF_UP);
                    return createLineItem(invoice, line.itemType(), line.description().trim(),
                            qty, total, order.getAndIncrement());
                })
                .toList();
    }

    private List<InvoiceItemEntity> buildElectricityItems(
            InvoiceEntity invoice,
            String meterNumber,
            BigDecimal unitsAllocated,
            BigDecimal ratePerUnitSnapshot,
            BigDecimal energyCharge,
            BigDecimal fixedAndAdditionalShare,
            BigDecimal taxAmountShare,
            BigDecimal creditShare
    ) {
        List<InvoiceItemEntity> items = new ArrayList<>();
        items.add(createLineItem(invoice, InvoiceItemType.ELECTRICITY,
                String.format("Electricity %s (%.2f units @ %.2f/unit)",
                        meterNumber, unitsAllocated, ratePerUnitSnapshot),
                BigDecimal.ONE, energyCharge, 0));
        if (fixedAndAdditionalShare.compareTo(BigDecimal.ZERO) > 0) {
            items.add(createLineItem(invoice, InvoiceItemType.ONE_TIME_CHARGE,
                    "Electricity fixed & utility share", BigDecimal.ONE, fixedAndAdditionalShare, items.size()));
        }
        if (taxAmountShare.compareTo(BigDecimal.ZERO) > 0) {
            items.add(createLineItem(invoice, InvoiceItemType.TAX,
                    "Electricity duty / GST share", BigDecimal.ONE, taxAmountShare, items.size()));
        }
        if (creditShare.compareTo(BigDecimal.ZERO) > 0) {
            items.add(createLineItem(invoice, InvoiceItemType.DISCOUNT,
                    "Electricity credit adjustment", BigDecimal.ONE, creditShare, items.size()));
        }
        return items;
    }

    private InvoiceItemEntity createLineItem(
            InvoiceEntity invoice,
            InvoiceItemType type,
            String description,
            BigDecimal quantity,
            BigDecimal total,
            int sortOrder
    ) {
        InvoiceItemEntity item = new InvoiceItemEntity();
        item.setInvoice(invoice);
        item.setItemType(type);
        item.setDescription(description);
        item.setQuantity(quantity.setScale(2, RoundingMode.HALF_UP));
        item.setUnitAmount(total.divide(quantity.max(BigDecimal.ONE), 2, RoundingMode.HALF_UP));
        item.setLineTotal(total.setScale(2, RoundingMode.HALF_UP));
        item.setSortOrder(sortOrder);
        return item;
    }

    private InvoiceDetailResponseDTO assembleDetail(InvoiceEntity invoice, List<InvoiceItemEntity> items) {
        var payments = paymentRepository.findAllByInvoiceIdOrderByPaidAtDescCreatedAtDesc(invoice.getId()).stream()
                .map(billingMapper::toPaymentResponseDTO).toList();
        var adjustments = paymentAdjustmentRepository.findAllByInvoiceIdOrderByAdjustedAtDesc(invoice.getId()).stream()
                .map(billingMapper::toPaymentAdjustmentResponseDTO).toList();
        var itemDtos = items.stream().map(billingMapper::toInvoiceItemResponseDTO).toList();
        return new InvoiceDetailResponseDTO(
                invoice.getId(), invoice.getOrganization().getId(),
                invoice.getProperty().getId(), invoice.getProperty().getName(),
                invoice.getTenant().getId(), invoice.getTenant().getFullName(), invoice.getTenant().getPhone(),
                Optional.ofNullable(invoice.getTenancy()).map(TenancyEntity::getId).orElse(null),
                Optional.ofNullable(invoice.getRoom()).map(room -> room.getNumber()).orElse(null),
                Optional.ofNullable(invoice.getBed()).map(bed -> bed.getBedCode()).orElse(null),
                invoice.getInvoiceNumber(), invoice.getInvoiceType(),
                invoice.getBillingYear(), invoice.getBillingMonth(),
                invoice.getPeriodStart(), invoice.getPeriodEnd(), invoice.getIssueDate(), invoice.getDueDate(),
                invoice.getSubtotalAmount(), invoice.getDiscountAmount(), invoice.getLateFeeAmount(),
                invoice.getTotalAmount(), invoice.getPaidAmount(), invoice.getBalanceDue(),
                billingMapper.resolveEffectiveStatus(invoice), invoice.getNotes(), invoice.getStatusReason(),
                invoice.getCreatedAt(), itemDtos, payments, adjustments
        );
    }
}

