package com.upcraft.stay.billing.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.upcraft.stay.billing.entity.InvoiceEntity;
import com.upcraft.stay.billing.entity.InvoiceItemEntity;
import com.upcraft.stay.common.enums.InvoiceItemType;
import com.upcraft.stay.common.enums.InvoiceStatus;
import com.upcraft.stay.common.enums.LateFeeType;
import com.upcraft.stay.property.entity.PropertyEntity;
import com.upcraft.stay.tenant.entity.TenancyEntity;

/**
 * Pure billing calculation helpers for proration, India calendar due dates, late fees, and numbering.
 */
@Component
public class InvoiceGenerationHelper {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    public LocalDate resolvePeriodDueDate(TenancyEntity tenancyEntity, YearMonth billingPeriod) {
        int rawDueDay = tenancyEntity.getRentDueDay() > 0 ? tenancyEntity.getRentDueDay() : 5;
        int clampedDay = Math.min(rawDueDay, billingPeriod.lengthOfMonth());
        return billingPeriod.atDay(clampedDay);
    }

    public BigDecimal computeMonthlyRentAmount(
            TenancyEntity tenancyEntity,
            YearMonth billingPeriod,
            boolean prorateMidMonth
    ) {
        BigDecimal fullRent = tenancyEntity.getAgreedMonthlyRent().setScale(2, RoundingMode.HALF_UP);
        if (!prorateMidMonth) {
            return fullRent;
        }
        LocalDate monthStart = billingPeriod.atDay(1);
        LocalDate monthEnd = billingPeriod.atEndOfMonth();
        LocalDate activeStart = tenancyEntity.getMoveInDate().isAfter(monthStart)
                ? tenancyEntity.getMoveInDate()
                : monthStart;
        LocalDate activeEnd = Optional.ofNullable(tenancyEntity.getActualMoveOutDate())
                .filter(moveOut -> moveOut.isBefore(monthEnd))
                .orElse(monthEnd);
        if (activeStart.equals(monthStart) && activeEnd.equals(monthEnd)) {
            return fullRent;
        }
        long activeDays = Math.max(0L, ChronoUnit.DAYS.between(activeStart, activeEnd) + 1L);
        return fullRent.multiply(BigDecimal.valueOf(activeDays))
                .divide(BigDecimal.valueOf(billingPeriod.lengthOfMonth()), 2, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateLateFee(PropertyEntity propertyEntity, InvoiceEntity invoiceEntity, LocalDate today) {
        LocalDate graceDeadline = invoiceEntity.getDueDate().plusDays(Math.max(0, propertyEntity.getGracePeriodDays()));
        if (!today.isAfter(graceDeadline) || invoiceEntity.getBalanceDue().compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal configuredAmount = propertyEntity.getLateFeeAmount();
        if (configuredAmount.compareTo(BigDecimal.ZERO) <= 0 || propertyEntity.getLateFeeType() == LateFeeType.NONE) {
            return BigDecimal.ZERO;
        }
        long overdueDays = ChronoUnit.DAYS.between(graceDeadline, today);
        return switch (propertyEntity.getLateFeeType()) {
            case FIXED -> configuredAmount.setScale(2, RoundingMode.HALF_UP);
            case PERCENTAGE -> invoiceEntity.getTotalAmount()
                    .multiply(configuredAmount)
                    .divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP);
            case PER_DAY -> configuredAmount.multiply(BigDecimal.valueOf(overdueDays))
                    .setScale(2, RoundingMode.HALF_UP);
            case NONE -> BigDecimal.ZERO;
        };
    }

    public void recalculateTotals(InvoiceEntity invoiceEntity, List<InvoiceItemEntity> items, LocalDate today) {
        BigDecimal subtotal = items.stream()
                .filter(item -> item.getItemType() != InvoiceItemType.DISCOUNT
                        && item.getItemType() != InvoiceItemType.LATE_FEE)
                .map(InvoiceItemEntity::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal discount = items.stream()
                .filter(item -> item.getItemType() == InvoiceItemType.DISCOUNT)
                .map(InvoiceItemEntity::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal lateFees = items.stream()
                .filter(item -> item.getItemType() == InvoiceItemType.LATE_FEE)
                .map(InvoiceItemEntity::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal total = subtotal.add(lateFees).subtract(discount).max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal paid = invoiceEntity.getPaidAmount().setScale(2, RoundingMode.HALF_UP);
        BigDecimal balance = total.subtract(paid).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

        invoiceEntity.setSubtotalAmount(subtotal.setScale(2, RoundingMode.HALF_UP));
        invoiceEntity.setDiscountAmount(discount.setScale(2, RoundingMode.HALF_UP));
        invoiceEntity.setLateFeeAmount(lateFees.setScale(2, RoundingMode.HALF_UP));
        invoiceEntity.setTotalAmount(total);
        invoiceEntity.setBalanceDue(balance);
        if (invoiceEntity.getInvoiceStatus() != InvoiceStatus.CANCELLED
                && invoiceEntity.getInvoiceStatus() != InvoiceStatus.WAIVED) {
            invoiceEntity.setInvoiceStatus(resolveOpenStatus(balance, paid, invoiceEntity.getDueDate(), today));
        }
    }

    public InvoiceStatus resolveOpenStatus(
            BigDecimal balanceDue,
            BigDecimal paidAmount,
            LocalDate dueDate,
            LocalDate today
    ) {
        if (balanceDue.compareTo(BigDecimal.ZERO) <= 0) {
            return InvoiceStatus.PAID;
        }
        if (dueDate.isBefore(today)) {
            return InvoiceStatus.OVERDUE;
        }
        return paidAmount.compareTo(BigDecimal.ZERO) > 0
                ? InvoiceStatus.PARTIALLY_PAID
                : InvoiceStatus.UNPAID;
    }

    public String nextInvoiceNumber(YearMonth billingPeriod, long sequenceNumber) {
        String shortSuffix = UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        return String.format("INV-%d%02d-%04d-%s",
                billingPeriod.getYear(), billingPeriod.getMonthValue(), sequenceNumber, shortSuffix);
    }
}
