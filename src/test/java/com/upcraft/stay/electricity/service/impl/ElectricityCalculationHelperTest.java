package com.upcraft.stay.electricity.service.impl;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.upcraft.stay.common.config.AppProperties;
import com.upcraft.stay.common.enums.SplitMethod;
import com.upcraft.stay.common.exception.ValidationException;
import com.upcraft.stay.electricity.dto.BillAllocationOverrideRequestDTO;
import com.upcraft.stay.electricity.entity.MeterReadingEntity;
import com.upcraft.stay.tenant.entity.TenancyEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ElectricityCalculationHelperTest {

    private final ElectricityCalculationHelper helper = new ElectricityCalculationHelper();

    @Test
    void detectUnusualUsage_flagsSpikeAboveHistoricalMultiplierAndMinThreshold() {
        AppProperties properties = new AppProperties();
        properties.getElectricity().setUnusualMultiplier(2.5);
        properties.getElectricity().setUnusualMinUnits(300.0);

        MeterReadingEntity r1 = new MeterReadingEntity();
        r1.setUnitsConsumed(new BigDecimal("100.00"));
        MeterReadingEntity r2 = new MeterReadingEntity();
        r2.setUnitsConsumed(new BigDecimal("120.00"));

        Optional<String> normal = helper.detectUnusualUsage(List.of(r1, r2), new BigDecimal("140.00"), properties);
        Optional<String> spike = helper.detectUnusualUsage(List.of(r1, r2), new BigDecimal("280.00"), properties);
        Optional<String> highMin = helper.detectUnusualUsage(List.of(), new BigDecimal("350.00"), properties);

        assertThat(normal).isEmpty();
        assertThat(spike).isPresent();
        assertThat(highMin).isPresent();
    }

    @Test
    void computeTenantShares_equalSplitAbsorbsRoundingRemainderCleanly() {
        MeterReadingEntity reading = sampleReading("100.00", "100.00", "0.00");
        List<TenancyEntity> tenancies = List.of(tenancyWithId(), tenancyWithId(), tenancyWithId());

        List<ElectricityCalculationHelper.CalculatedShare> shares =
                helper.computeTenantShares(reading, SplitMethod.EQUAL, tenancies, null);

        BigDecimal totalSum = shares.stream()
                .map(ElectricityCalculationHelper.CalculatedShare::totalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(shares).hasSize(3);
        assertThat(totalSum).isEqualByComparingTo("100.00");
    }

    @Test
    void computeTenantShares_percentageAndManual_validateSumAndComputeShares() {
        MeterReadingEntity reading = sampleReading("100.00", "900.00", "100.00");
        TenancyEntity t1 = tenancyWithId();
        TenancyEntity t2 = tenancyWithId();

        List<BillAllocationOverrideRequestDTO> badPct = List.of(
                new BillAllocationOverrideRequestDTO(t1.getId(), new BigDecimal("60.00"), null),
                new BillAllocationOverrideRequestDTO(t2.getId(), new BigDecimal("30.00"), null)
        );
        assertThatThrownBy(() -> helper.computeTenantShares(reading, SplitMethod.PERCENTAGE, List.of(t1, t2), badPct))
                .isInstanceOf(ValidationException.class);

        List<BillAllocationOverrideRequestDTO> goodPct = List.of(
                new BillAllocationOverrideRequestDTO(t1.getId(), new BigDecimal("70.00"), null),
                new BillAllocationOverrideRequestDTO(t2.getId(), new BigDecimal("30.00"), null)
        );
        List<ElectricityCalculationHelper.CalculatedShare> shares =
                helper.computeTenantShares(reading, SplitMethod.PERCENTAGE, List.of(t1, t2), goodPct);

        assertThat(shares.get(0).totalAmount()).isEqualByComparingTo("700.00");
        assertThat(shares.get(1).totalAmount()).isEqualByComparingTo("300.00");
        assertThat(helper.nextBillNumber(YearMonth.of(2026, 11), 12)).isEqualTo("ELC-202611-0012");
    }

    private MeterReadingEntity sampleReading(String units, String energy, String fixed) {
        MeterReadingEntity reading = new MeterReadingEntity();
        reading.setUnitsConsumed(new BigDecimal(units));
        reading.setEnergyCharge(new BigDecimal(energy));
        reading.setFixedChargeSnapshot(new BigDecimal(fixed));
        reading.setAdditionalCharge(BigDecimal.ZERO);
        reading.setTaxAmount(BigDecimal.ZERO);
        reading.setCreditAmount(BigDecimal.ZERO);
        reading.setTotalAmount(new BigDecimal(energy).add(new BigDecimal(fixed)));
        return reading;
    }

    private TenancyEntity tenancyWithId() {
        TenancyEntity entity = new TenancyEntity();
        entity.setId(UUID.randomUUID());
        return entity;
    }
}

