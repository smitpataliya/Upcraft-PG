package com.upcraft.stay.billing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.upcraft.stay.billing.dto.RecordPaymentRequestDTO;
import com.upcraft.stay.billing.entity.InvoiceEntity;
import com.upcraft.stay.billing.entity.PaymentEntity;
import com.upcraft.stay.billing.service.GatewayRecordingResult;
import com.upcraft.stay.billing.service.impl.InvoiceGenerationHelper;
import com.upcraft.stay.billing.service.impl.ManualRecordingGatewayAdapter;
import com.upcraft.stay.billing.service.impl.PaymentSettlementHelper;
import com.upcraft.stay.common.enums.InvoiceStatus;
import com.upcraft.stay.common.enums.LateFeeType;
import com.upcraft.stay.common.enums.PaymentMethod;
import com.upcraft.stay.common.enums.PaymentStatus;
import com.upcraft.stay.common.exception.ValidationException;
import com.upcraft.stay.property.entity.PropertyEntity;
import com.upcraft.stay.tenant.entity.TenancyEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BillingServiceUnitTest {

    private final InvoiceGenerationHelper generationHelper = new InvoiceGenerationHelper();
    private final PaymentSettlementHelper settlementHelper = new PaymentSettlementHelper();
    private final ManualRecordingGatewayAdapter gatewayAdapter = new ManualRecordingGatewayAdapter();

    @Test
    void resolvePeriodDueDate_clampsFebruaryLeapAndNonLeapMonths() {
        TenancyEntity tenancy = new TenancyEntity();
        tenancy.setRentDueDay(31);

        assertThat(generationHelper.resolvePeriodDueDate(tenancy, YearMonth.of(2026, 2)))
                .isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(generationHelper.resolvePeriodDueDate(tenancy, YearMonth.of(2024, 2)))
                .isEqualTo(LocalDate.of(2024, 2, 29));
    }

    @Test
    void computeMonthlyRentAmount_proratesMidMonthMoveInAccurately() {
        TenancyEntity tenancy = new TenancyEntity();
        tenancy.setAgreedMonthlyRent(new BigDecimal("9000.00"));
        tenancy.setMoveInDate(LocalDate.of(2026, 11, 16));

        BigDecimal prorated = generationHelper.computeMonthlyRentAmount(tenancy, YearMonth.of(2026, 11), true);
        assertThat(prorated).isEqualByComparingTo("4500.00");

        BigDecimal full = generationHelper.computeMonthlyRentAmount(tenancy, YearMonth.of(2026, 11), false);
        assertThat(full).isEqualByComparingTo("9000.00");
    }

    @Test
    void calculateLateFee_respectsGracePeriodAndPerDayRule() {
        PropertyEntity property = new PropertyEntity();
        property.setLateFeeType(LateFeeType.PER_DAY);
        property.setLateFeeAmount(new BigDecimal("100.00"));
        property.setGracePeriodDays(3);

        InvoiceEntity invoice = new InvoiceEntity();
        invoice.setDueDate(LocalDate.of(2026, 11, 5));
        invoice.setTotalAmount(new BigDecimal("10000.00"));
        invoice.setBalanceDue(new BigDecimal("10000.00"));

        assertThat(generationHelper.calculateLateFee(property, invoice, LocalDate.of(2026, 11, 8)))
                .isEqualByComparingTo("0.00");
        assertThat(generationHelper.calculateLateFee(property, invoice, LocalDate.of(2026, 11, 11)))
                .isEqualByComparingTo("300.00");
    }

    @Test
    void gatewayAdapter_rejectsUnverifiedGatewayWithoutReferenceAndRecomputesBalancesCleanly() {
        RecordPaymentRequestDTO unverifiedGateway = new RecordPaymentRequestDTO(
                new BigDecimal("5000.00"), null, PaymentMethod.GATEWAY, null, null, null, null
        );
        assertThatThrownBy(() -> gatewayAdapter.verifyAndResolve(unverifiedGateway))
                .isInstanceOf(ValidationException.class);

        GatewayRecordingResult verified = gatewayAdapter.verifyAndResolve(new RecordPaymentRequestDTO(
                new BigDecimal("5000.00"), null, PaymentMethod.UPI, "UPI-998877", null, null, null
        ));
        assertThat(verified.paymentStatus()).isEqualTo(PaymentStatus.COMPLETED);

        InvoiceEntity invoice = new InvoiceEntity();
        invoice.setTotalAmount(new BigDecimal("10000.00"));
        invoice.setDueDate(LocalDate.of(2099, 12, 5));
        invoice.setInvoiceStatus(InvoiceStatus.UNPAID);

        PaymentEntity completed = new PaymentEntity();
        completed.setAmount(new BigDecimal("4000.00"));
        completed.setPaymentStatus(PaymentStatus.COMPLETED);
        PaymentEntity reversed = new PaymentEntity();
        reversed.setAmount(new BigDecimal("3000.00"));
        reversed.setPaymentStatus(PaymentStatus.REVERSED);

        settlementHelper.recomputeInvoiceFromPayments(invoice, List.of(completed, reversed), generationHelper);
        assertThat(invoice.getPaidAmount()).isEqualByComparingTo("4000.00");
        assertThat(invoice.getBalanceDue()).isEqualByComparingTo("6000.00");
        assertThat(invoice.getInvoiceStatus()).isEqualTo(InvoiceStatus.PARTIALLY_PAID);
    }
}

