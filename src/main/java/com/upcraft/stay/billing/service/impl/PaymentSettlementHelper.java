package com.upcraft.stay.billing.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.upcraft.stay.billing.entity.InvoiceEntity;
import com.upcraft.stay.billing.entity.PaymentEntity;
import com.upcraft.stay.common.constants.AppConstants;
import com.upcraft.stay.common.enums.InvoiceStatus;
import com.upcraft.stay.common.enums.PaymentStatus;

/**
 * Helper recomputing invoice settlement balances and generating unique payment receipt numbers.
 */
@Component
public class PaymentSettlementHelper {

    private static final ZoneId INDIA_ZONE = ZoneId.of(AppConstants.DEFAULT_TIMEZONE);
    private static final DateTimeFormatter RECEIPT_PERIOD_FMT = DateTimeFormatter.ofPattern("yyyyMM");

    public void recomputeInvoiceFromPayments(
            InvoiceEntity invoiceEntity,
            List<PaymentEntity> invoicePayments,
            InvoiceGenerationHelper generationHelper
    ) {
        BigDecimal totalPaid = invoicePayments.stream()
                .filter(payment -> payment.getPaymentStatus() == PaymentStatus.COMPLETED)
                .map(PaymentEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal balanceDue = invoiceEntity.getTotalAmount()
                .subtract(totalPaid)
                .max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);

        invoiceEntity.setPaidAmount(totalPaid);
        invoiceEntity.setBalanceDue(balanceDue);
        if (invoiceEntity.getInvoiceStatus() != InvoiceStatus.CANCELLED
                && invoiceEntity.getInvoiceStatus() != InvoiceStatus.WAIVED) {
            LocalDate today = LocalDate.now(INDIA_ZONE);
            invoiceEntity.setInvoiceStatus(
                    generationHelper.resolveOpenStatus(balanceDue, totalPaid, invoiceEntity.getDueDate(), today)
            );
        }
    }

    public String nextReceiptNumber(long sequenceNumber) {
        String periodToken = LocalDate.now(INDIA_ZONE).format(RECEIPT_PERIOD_FMT);
        String randomToken = UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        return String.format("RCP-%s-%04d-%s", periodToken, sequenceNumber, randomToken);
    }
}

