package com.upcraft.stay.reporting.service.impl;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import com.upcraft.stay.billing.entity.DepositTransactionEntity;
import com.upcraft.stay.billing.entity.InvoiceEntity;
import com.upcraft.stay.billing.entity.PaymentEntity;
import com.upcraft.stay.billing.repository.DepositTransactionRepository;
import com.upcraft.stay.billing.repository.InvoiceRepository;
import com.upcraft.stay.billing.repository.PaymentRepository;
import com.upcraft.stay.common.enums.DepositTransactionType;
import com.upcraft.stay.common.enums.ElectricityBillStatus;
import com.upcraft.stay.common.enums.InvoiceStatus;
import com.upcraft.stay.common.enums.PaymentStatus;
import com.upcraft.stay.domain.entity.Room;
import com.upcraft.stay.domain.entity.User;
import com.upcraft.stay.electricity.entity.ElectricityBillEntity;
import com.upcraft.stay.electricity.repository.ElectricityBillRepository;
import com.upcraft.stay.organization.entity.OrganizationEntity;
import com.upcraft.stay.reporting.dto.ReportResponseDTO;
import com.upcraft.stay.reporting.enums.ReportType;
import com.upcraft.stay.room.entity.BedEntity;

/**
 * Builds financial and utility report payloads (rent collection, outstanding, payments, deposits, electricity).
 */
@Component
@RequiredArgsConstructor
public class FinancialReportBuilder {

    private static final Set<InvoiceStatus> COLLECTION_STATUSES = EnumSet.of(
            InvoiceStatus.UNPAID, InvoiceStatus.PARTIALLY_PAID, InvoiceStatus.PAID, InvoiceStatus.OVERDUE
    );
    private static final Set<InvoiceStatus> OPEN_STATUSES = EnumSet.of(
            InvoiceStatus.UNPAID, InvoiceStatus.PARTIALLY_PAID, InvoiceStatus.OVERDUE
    );

    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final DepositTransactionRepository depositTransactionRepository;
    private final ElectricityBillRepository electricityBillRepository;
    private final ReportSupport support;

    public ReportResponseDTO buildRentCollection(
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
        Pageable pageable = support.buildReportPageable(page, size, exportAll, Sort.by(Sort.Direction.DESC, "dueDate"));
        Page<InvoiceEntity> invoicePage = invoiceRepository.findReportInvoices(
                propertyIds, COLLECTION_STATUSES, false,
                Optional.ofNullable(fromDate).isPresent(), fromDate,
                Optional.ofNullable(toDate).isPresent(), toDate, pageable
        );
        List<Map<String, Object>> rows = invoicePage.getContent().stream().map(this::mapRentRow).toList();
        BigDecimal totalBilled = invoicePage.getContent().stream()
                .map(InvoiceEntity::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCollected = invoicePage.getContent().stream()
                .map(InvoiceEntity::getPaidAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalDue = invoicePage.getContent().stream()
                .map(InvoiceEntity::getBalanceDue).reduce(BigDecimal.ZERO, BigDecimal::add);
        return support.buildResponse(
                ReportType.RENT_COLLECTION,
                "Rent Collection Report",
                organization,
                scopeLabel,
                fromDate,
                toDate,
                actorUser,
                List.of("invoiceNumber", "property", "tenant", "roomBed", "period", "dueDate", "billed", "paid", "balanceDue", "status"),
                rows,
                Map.of("totalBilled", totalBilled, "totalCollected", totalCollected, "totalBalanceDue", totalDue),
                invoicePage
        );
    }

    public ReportResponseDTO buildOutstanding(
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
        Pageable pageable = support.buildReportPageable(page, size, exportAll, Sort.by(Sort.Direction.ASC, "dueDate"));
        Page<InvoiceEntity> invoicePage = invoiceRepository.findReportInvoices(
                propertyIds, OPEN_STATUSES, true,
                Optional.ofNullable(fromDate).isPresent(), fromDate,
                Optional.ofNullable(toDate).isPresent(), toDate, pageable
        );
        List<Map<String, Object>> rows = invoicePage.getContent().stream().map(this::mapOutstandingRow).toList();
        BigDecimal totalOutstanding = invoicePage.getContent().stream()
                .map(InvoiceEntity::getBalanceDue).reduce(BigDecimal.ZERO, BigDecimal::add);
        return support.buildResponse(
                ReportType.OUTSTANDING,
                "Outstanding & Overdue Dues Report",
                organization,
                scopeLabel,
                fromDate,
                toDate,
                actorUser,
                List.of("invoiceNumber", "property", "tenant", "phone", "type", "dueDate", "daysOverdue", "balanceDue", "status"),
                rows,
                Map.of("totalOutstanding", totalOutstanding),
                invoicePage
        );
    }

    public ReportResponseDTO buildPayments(
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
        Instant fromInstant = Optional.ofNullable(fromDate).map(date -> date.atStartOfDay(support.zoneId()).toInstant()).orElse(null);
        Instant toInstant = Optional.ofNullable(toDate).map(date -> date.atTime(LocalTime.MAX).atZone(support.zoneId()).toInstant()).orElse(null);
        Pageable pageable = support.buildReportPageable(page, size, exportAll, Sort.by(Sort.Direction.DESC, "paidAt"));
        Page<PaymentEntity> paymentPage = paymentRepository.findReportPayments(
                propertyIds,
                Optional.ofNullable(fromInstant).isPresent(), fromInstant,
                Optional.ofNullable(toInstant).isPresent(), toInstant, pageable
        );
        List<Map<String, Object>> rows = paymentPage.getContent().stream().map(this::mapPaymentRow).toList();
        BigDecimal completedSum = paymentPage.getContent().stream()
                .filter(paymentEntity -> paymentEntity.getPaymentStatus() == PaymentStatus.COMPLETED)
                .map(PaymentEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return support.buildResponse(
                ReportType.PAYMENTS,
                "Payment Receipts Report",
                organization,
                scopeLabel,
                fromDate,
                toDate,
                actorUser,
                List.of("receiptNumber", "invoiceNumber", "property", "tenant", "paidAt", "method", "reference", "amount", "status"),
                rows,
                Map.of("completedReceiptsTotal", completedSum),
                paymentPage
        );
    }

    public ReportResponseDTO buildDeposits(
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
        Pageable pageable = support.buildReportPageable(page, size, exportAll, Sort.by(Sort.Direction.DESC, "transactionDate"));
        Page<DepositTransactionEntity> txPage = depositTransactionRepository.findAccessibleDepositReport(
                propertyIds,
                Optional.ofNullable(fromDate).isPresent(), fromDate,
                Optional.ofNullable(toDate).isPresent(), toDate, pageable
        );
        List<Map<String, Object>> rows = txPage.getContent().stream().map(this::mapDepositRow).toList();
        BigDecimal collected = sumDepositType(txPage.getContent(), DepositTransactionType.COLLECTED);
        BigDecimal refunded = sumDepositType(txPage.getContent(), DepositTransactionType.REFUND);
        BigDecimal deductions = sumDepositType(txPage.getContent(), DepositTransactionType.DEDUCTION);
        return support.buildResponse(
                ReportType.DEPOSITS,
                "Security Deposit Ledger Report",
                organization,
                scopeLabel,
                fromDate,
                toDate,
                actorUser,
                List.of("date", "property", "tenant", "type", "method", "amount", "reason", "recordedBy"),
                rows,
                Map.of("collected", collected, "refunded", refunded, "deductions", deductions),
                txPage
        );
    }

    public ReportResponseDTO buildElectricity(
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
        Instant fromInstant = Optional.ofNullable(fromDate).map(date -> date.atStartOfDay(support.zoneId()).toInstant()).orElse(null);
        Instant toInstant = Optional.ofNullable(toDate).map(date -> date.atTime(LocalTime.MAX).atZone(support.zoneId()).toInstant()).orElse(null);
        Pageable pageable = support.buildReportPageable(page, size, exportAll, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ElectricityBillEntity> billPage = electricityBillRepository.findReportElectricityBills(
                propertyIds,
                Optional.ofNullable(fromInstant).isPresent(), fromInstant,
                Optional.ofNullable(toInstant).isPresent(), toInstant, pageable
        );
        List<Map<String, Object>> rows = billPage.getContent().stream().map(this::mapElectricityRow).toList();
        BigDecimal totalUnits = billPage.getContent().stream()
                .filter(bill -> bill.getBillStatus() != ElectricityBillStatus.CANCELLED)
                .map(ElectricityBillEntity::getUnitsAllocated).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalAmount = billPage.getContent().stream()
                .filter(bill -> bill.getBillStatus() != ElectricityBillStatus.CANCELLED)
                .map(ElectricityBillEntity::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return support.buildResponse(
                ReportType.ELECTRICITY,
                "Electricity Billing & Consumption Report",
                organization,
                scopeLabel,
                fromDate,
                toDate,
                actorUser,
                List.of("billNumber", "property", "meter", "tenant", "period", "units", "totalAmount", "status"),
                rows,
                Map.of("totalUnits", totalUnits, "totalAmount", totalAmount),
                billPage
        );
    }

    private BigDecimal sumDepositType(List<DepositTransactionEntity> transactions, DepositTransactionType type) {
        return transactions.stream()
                .filter(depositTx -> depositTx.getTransactionType() == type)
                .map(DepositTransactionEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Map<String, Object> mapRentRow(InvoiceEntity invoiceEntity) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("invoiceNumber", invoiceEntity.getInvoiceNumber());
        row.put("property", invoiceEntity.getProperty().getName());
        row.put("tenant", invoiceEntity.getTenant().getFullName());
        row.put("roomBed", formatRoomBed(invoiceEntity.getRoom(), invoiceEntity.getBed()));
        row.put("period", String.format("%d-%02d", invoiceEntity.getBillingYear(), invoiceEntity.getBillingMonth()));
        row.put("dueDate", invoiceEntity.getDueDate().toString());
        row.put("billed", invoiceEntity.getTotalAmount());
        row.put("paid", invoiceEntity.getPaidAmount());
        row.put("balanceDue", invoiceEntity.getBalanceDue());
        row.put("status", invoiceEntity.getInvoiceStatus().name());
        return row;
    }

    private Map<String, Object> mapOutstandingRow(InvoiceEntity invoiceEntity) {
        long daysOverdue = Math.max(0L, ChronoUnit.DAYS.between(invoiceEntity.getDueDate(), LocalDate.now(support.zoneId())));
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("invoiceNumber", invoiceEntity.getInvoiceNumber());
        row.put("property", invoiceEntity.getProperty().getName());
        row.put("tenant", invoiceEntity.getTenant().getFullName());
        row.put("phone", invoiceEntity.getTenant().getPhone());
        row.put("type", invoiceEntity.getInvoiceType().name());
        row.put("dueDate", invoiceEntity.getDueDate().toString());
        row.put("daysOverdue", daysOverdue);
        row.put("balanceDue", invoiceEntity.getBalanceDue());
        row.put("status", daysOverdue > 0 ? InvoiceStatus.OVERDUE.name() : invoiceEntity.getInvoiceStatus().name());
        return row;
    }

    private Map<String, Object> mapPaymentRow(PaymentEntity paymentEntity) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("receiptNumber", paymentEntity.getReceiptNumber());
        row.put("invoiceNumber", paymentEntity.getInvoice().getInvoiceNumber());
        row.put("property", paymentEntity.getProperty().getName());
        row.put("tenant", paymentEntity.getTenant().getFullName());
        row.put("paidAt", support.formatInstantIst(paymentEntity.getPaidAt()));
        row.put("method", paymentEntity.getPaymentMethod().name());
        row.put("reference", StringUtils.defaultIfBlank(paymentEntity.getReferenceNumber(), "-"));
        row.put("amount", paymentEntity.getAmount());
        row.put("status", paymentEntity.getPaymentStatus().name());
        return row;
    }

    private Map<String, Object> mapDepositRow(DepositTransactionEntity depositTx) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("date", depositTx.getTransactionDate().toString());
        row.put("property", depositTx.getProperty().getName());
        row.put("tenant", depositTx.getTenant().getFullName());
        row.put("type", depositTx.getTransactionType().name());
        row.put("method", depositTx.getPaymentMethod().name());
        row.put("amount", depositTx.getAmount());
        row.put("reason", StringUtils.defaultIfBlank(depositTx.getReason(), "-"));
        row.put("recordedBy", Optional.ofNullable(depositTx.getRecordedByUser()).map(User::getName).orElse("System"));
        return row;
    }

    private Map<String, Object> mapElectricityRow(ElectricityBillEntity billEntity) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("billNumber", billEntity.getBillNumber());
        row.put("property", billEntity.getProperty().getName());
        row.put("meter", billEntity.getMeter().getMeterNumber());
        row.put("tenant", billEntity.getTenant().getFullName());
        row.put("period", String.format("%d-%02d", billEntity.getBillingYear(), billEntity.getBillingMonth()));
        row.put("units", billEntity.getUnitsAllocated());
        row.put("totalAmount", billEntity.getTotalAmount());
        row.put("status", billEntity.getBillStatus().name());
        return row;
    }

    private String formatRoomBed(Room roomEntity, BedEntity bedEntity) {
        String roomLabel = Optional.ofNullable(roomEntity).map(Room::getNumber).orElse("-");
        String bedLabel = Optional.ofNullable(bedEntity).map(BedEntity::getBedCode).orElse("-");
        return String.format("R%s / %s", roomLabel, bedLabel);
    }
}

