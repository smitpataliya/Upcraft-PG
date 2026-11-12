package com.upcraft.stay.billing.service.impl;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import com.upcraft.stay.billing.dto.LedgerEntryResponseDTO;
import com.upcraft.stay.billing.dto.TenantLedgerResponseDTO;
import com.upcraft.stay.billing.entity.DepositTransactionEntity;
import com.upcraft.stay.billing.entity.InvoiceEntity;
import com.upcraft.stay.billing.entity.PaymentAdjustmentEntity;
import com.upcraft.stay.billing.entity.PaymentEntity;
import com.upcraft.stay.common.constants.AppConstants;
import com.upcraft.stay.common.dto.PageResponseDTO;
import com.upcraft.stay.common.enums.DepositTransactionType;
import com.upcraft.stay.common.enums.InvoiceStatus;
import com.upcraft.stay.tenant.entity.TenantEntity;

/**
 * Composes unified chronological tenant financial ledger summaries and paginated entries.
 */
@Component
@RequiredArgsConstructor
public class TenantLedgerBuilder {

    private static final ZoneId INDIA_ZONE = ZoneId.of(AppConstants.DEFAULT_TIMEZONE);

    public TenantLedgerResponseDTO build(
            TenantEntity tenant,
            List<InvoiceEntity> invoices,
            List<PaymentEntity> payments,
            List<PaymentAdjustmentEntity> adjustments,
            List<DepositTransactionEntity> deposits,
            Pageable pageable
    ) {
        BigDecimal totalBilled = invoices.stream()
                .filter(inv -> inv.getInvoiceStatus() != InvoiceStatus.CANCELLED
                        && inv.getInvoiceStatus() != InvoiceStatus.WAIVED)
                .map(InvoiceEntity::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalPaid = invoices.stream()
                .filter(inv -> inv.getInvoiceStatus() != InvoiceStatus.CANCELLED
                        && inv.getInvoiceStatus() != InvoiceStatus.WAIVED)
                .map(InvoiceEntity::getPaidAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalOutstanding = invoices.stream()
                .filter(inv -> inv.getInvoiceStatus() != InvoiceStatus.CANCELLED
                        && inv.getInvoiceStatus() != InvoiceStatus.WAIVED)
                .map(InvoiceEntity::getBalanceDue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal depositHeld = computeDepositHeld(deposits);

        List<LedgerEntryResponseDTO> sortedEntries = Stream.of(
                        invoices.stream().map(this::mapInvoiceEntry),
                        payments.stream().map(this::mapPaymentEntry),
                        adjustments.stream().map(this::mapAdjustmentEntry),
                        deposits.stream().map(this::mapDepositEntry)
                )
                .flatMap(stream -> stream)
                .sorted(Comparator.comparing(LedgerEntryResponseDTO::entryDate).reversed())
                .toList();

        int startIndex = Math.min((int) pageable.getOffset(), sortedEntries.size());
        int endIndex = Math.min(startIndex + pageable.getPageSize(), sortedEntries.size());
        List<LedgerEntryResponseDTO> pageSlice = sortedEntries.subList(startIndex, endIndex);
        PageResponseDTO<LedgerEntryResponseDTO> paged = PageResponseDTO.from(
                new PageImpl<>(pageSlice, pageable, sortedEntries.size())
        );

        return new TenantLedgerResponseDTO(
                tenant.getId(), tenant.getFullName(), tenant.getPhone(),
                tenant.getProperty().getId(), tenant.getProperty().getName(),
                totalBilled, totalPaid, totalOutstanding, depositHeld, paged
        );
    }

    private BigDecimal computeDepositHeld(List<DepositTransactionEntity> deposits) {
        BigDecimal inflows = deposits.stream()
                .filter(d -> d.getTransactionType() == DepositTransactionType.COLLECTED
                        || d.getTransactionType() == DepositTransactionType.ADJUSTMENT)
                .map(DepositTransactionEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal outflows = deposits.stream()
                .filter(d -> d.getTransactionType() == DepositTransactionType.DEDUCTION
                        || d.getTransactionType() == DepositTransactionType.REFUND
                        || d.getTransactionType() == DepositTransactionType.FORFEIT)
                .map(DepositTransactionEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return inflows.subtract(outflows).max(BigDecimal.ZERO);
    }

    private LedgerEntryResponseDTO mapInvoiceEntry(InvoiceEntity inv) {
        return new LedgerEntryResponseDTO(
                inv.getId(), inv.getIssueDate(), "INVOICE",
                String.format("Invoice %d-%02d", inv.getBillingYear(), inv.getBillingMonth()),
                inv.getInvoiceNumber(), inv.getTotalAmount(), BigDecimal.ZERO,
                inv.getInvoiceStatus().name(), inv.getNotes()
        );
    }

    private LedgerEntryResponseDTO mapPaymentEntry(PaymentEntity pay) {
        return new LedgerEntryResponseDTO(
                pay.getId(), pay.getPaidAt().atZone(INDIA_ZONE).toLocalDate(), "PAYMENT",
                String.format("Payment (%s)", pay.getPaymentMethod().name()),
                pay.getReceiptNumber(), BigDecimal.ZERO, pay.getAmount(),
                pay.getPaymentStatus().name(), pay.getNotes()
        );
    }

    private LedgerEntryResponseDTO mapAdjustmentEntry(PaymentAdjustmentEntity adj) {
        return new LedgerEntryResponseDTO(
                adj.getId(), adj.getAdjustedAt().atZone(INDIA_ZONE).toLocalDate(), "ADJUSTMENT",
                "Payment " + adj.getAdjustmentType().name(),
                adj.getPayment().getReceiptNumber(), adj.getAmount(), BigDecimal.ZERO,
                adj.getAdjustmentType().name(), adj.getReason()
        );
    }

    private LedgerEntryResponseDTO mapDepositEntry(DepositTransactionEntity dep) {
        boolean isCredit = dep.getTransactionType() == DepositTransactionType.COLLECTED
                || dep.getTransactionType() == DepositTransactionType.ADJUSTMENT;
        return new LedgerEntryResponseDTO(
                dep.getId(), dep.getTransactionDate(), "DEPOSIT",
                "Security Deposit " + dep.getTransactionType().name(),
                dep.getReferenceNumber(),
                isCredit ? BigDecimal.ZERO : dep.getAmount(),
                isCredit ? dep.getAmount() : BigDecimal.ZERO,
                dep.getTransactionType().name(), dep.getReason()
        );
    }
}

