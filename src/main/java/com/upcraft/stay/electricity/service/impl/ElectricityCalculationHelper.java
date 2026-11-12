package com.upcraft.stay.electricity.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

import com.upcraft.stay.common.config.AppProperties;
import com.upcraft.stay.common.enums.SplitMethod;
import com.upcraft.stay.common.exception.ValidationException;
import com.upcraft.stay.electricity.dto.BillAllocationOverrideRequestDTO;
import com.upcraft.stay.electricity.entity.MeterReadingEntity;
import com.upcraft.stay.tenant.entity.TenancyEntity;

/**
 * Pure financial and statistical calculations for electricity usage detection and shared bill splits.
 */
@Component
public class ElectricityCalculationHelper {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    public Optional<String> detectUnusualUsage(
            List<MeterReadingEntity> priorReadings,
            BigDecimal currentUnits,
            AppProperties appProperties
    ) {
        BigDecimal minThreshold = BigDecimal.valueOf(appProperties.getElectricity().getUnusualMinUnits());
        BigDecimal multiplier = BigDecimal.valueOf(appProperties.getElectricity().getUnusualMultiplier());

        if (CollectionUtils.isNotEmpty(priorReadings)) {
            BigDecimal sum = priorReadings.stream()
                    .map(MeterReadingEntity::getUnitsConsumed)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal average = sum.divide(BigDecimal.valueOf(priorReadings.size()), 2, RoundingMode.HALF_UP);
            BigDecimal spikeLimit = average.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
            if (average.compareTo(BigDecimal.ZERO) > 0 && currentUnits.compareTo(spikeLimit) >= 0) {
                return Optional.of(String.format("Units (%.2f) exceed %.1fx historical avg (%.2f)",
                        currentUnits, multiplier, average));
            }
        }
        if (currentUnits.compareTo(minThreshold) >= 0) {
            return Optional.of(String.format("High monthly consumption alert (%.2f units >= %.0f threshold)",
                    currentUnits, minThreshold));
        }
        return Optional.empty();
    }

    public List<CalculatedShare> computeTenantShares(
            MeterReadingEntity reading,
            SplitMethod splitMethod,
            List<TenancyEntity> targetTenancies,
            List<BillAllocationOverrideRequestDTO> overrides
    ) {
        if (CollectionUtils.isEmpty(targetTenancies)) {
            throw new ValidationException("At least one active tenancy is required to split electricity bill");
        }
        return switch (splitMethod) {
            case SINGLE_TENANT -> computeSingleShare(reading, targetTenancies);
            case EQUAL, PER_BED -> computeEqualShares(reading, targetTenancies);
            case PERCENTAGE -> computePercentageShares(reading, targetTenancies, overrides);
            case MANUAL -> computeManualShares(reading, targetTenancies, overrides);
        };
    }

    public String nextBillNumber(YearMonth period, long sequence) {
        return String.format("ELC-%d%02d-%04d", period.getYear(), period.getMonthValue(), sequence);
    }

    private List<CalculatedShare> computeSingleShare(MeterReadingEntity reading, List<TenancyEntity> tenancies) {
        if (tenancies.size() > 1) {
            return computeEqualShares(reading, tenancies);
        }
        return List.of(buildShare(tenancies.get(0), ONE_HUNDRED,
                reading.getUnitsConsumed(), reading.getEnergyCharge(), reading.getFixedChargeSnapshot(),
                reading.getAdditionalCharge(), reading.getTaxAmount(), reading.getCreditAmount(), reading.getTotalAmount()));
    }

    private List<CalculatedShare> computeEqualShares(MeterReadingEntity reading, List<TenancyEntity> tenancies) {
        int count = tenancies.size();
        BigDecimal countDecimal = BigDecimal.valueOf(count);
        List<BigDecimal> pctSplits = splitAcross(ONE_HUNDRED, countDecimal, count);
        List<BigDecimal> unitSplits = splitAcross(reading.getUnitsConsumed(), countDecimal, count);
        List<BigDecimal> energySplits = splitAcross(reading.getEnergyCharge(), countDecimal, count);
        List<BigDecimal> fixedSplits = splitAcross(reading.getFixedChargeSnapshot(), countDecimal, count);
        List<BigDecimal> extraSplits = splitAcross(reading.getAdditionalCharge(), countDecimal, count);
        List<BigDecimal> taxSplits = splitAcross(reading.getTaxAmount(), countDecimal, count);
        List<BigDecimal> creditSplits = splitAcross(reading.getCreditAmount(), countDecimal, count);
        List<BigDecimal> totalSplits = splitAcross(reading.getTotalAmount(), countDecimal, count);

        return IntStream.range(0, count)
                .mapToObj(index -> buildShare(
                        tenancies.get(index), pctSplits.get(index), unitSplits.get(index),
                        energySplits.get(index), fixedSplits.get(index), extraSplits.get(index),
                        taxSplits.get(index), creditSplits.get(index), totalSplits.get(index)))
                .toList();
    }

    private List<CalculatedShare> computePercentageShares(
            MeterReadingEntity reading,
            List<TenancyEntity> tenancies,
            List<BillAllocationOverrideRequestDTO> overrides
    ) {
        if (CollectionUtils.isEmpty(overrides)) {
            throw new ValidationException("Percentage split requires tenant percentage allocations");
        }
        BigDecimal pctSum = overrides.stream()
                .map(override -> Optional.ofNullable(override.sharePercentage()).orElse(BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (pctSum.compareTo(ONE_HUNDRED) != 0) {
            throw new ValidationException("Percentage allocations must sum to exactly 100.00%");
        }
        Map<UUID, TenancyEntity> tenancyMap = tenancies.stream()
                .collect(Collectors.toMap(TenancyEntity::getId, Function.identity()));
        List<BigDecimal> ratios = overrides.stream()
                .map(override -> override.sharePercentage().divide(ONE_HUNDRED, 6, RoundingMode.HALF_UP))
                .toList();
        return allocateByRatios(reading, overrides, tenancyMap, ratios);
    }

    private List<CalculatedShare> computeManualShares(
            MeterReadingEntity reading,
            List<TenancyEntity> tenancies,
            List<BillAllocationOverrideRequestDTO> overrides
    ) {
        if (CollectionUtils.isEmpty(overrides)) {
            throw new ValidationException("Manual split requires explicit amount allocations");
        }
        BigDecimal manualSum = overrides.stream()
                .map(override -> Optional.ofNullable(override.manualAmount()).orElse(BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        if (manualSum.compareTo(reading.getTotalAmount()) != 0) {
            throw new ValidationException(String.format(
                    "Manual amounts (%.2f) must match total electricity reading amount (%.2f)",
                    manualSum, reading.getTotalAmount()));
        }
        Map<UUID, TenancyEntity> tenancyMap = tenancies.stream()
                .collect(Collectors.toMap(TenancyEntity::getId, Function.identity()));
        BigDecimal divisor = reading.getTotalAmount().compareTo(BigDecimal.ZERO) > 0
                ? reading.getTotalAmount()
                : BigDecimal.ONE;
        List<BigDecimal> ratios = overrides.stream()
                .map(override -> override.manualAmount().divide(divisor, 6, RoundingMode.HALF_UP))
                .toList();
        return allocateByRatios(reading, overrides, tenancyMap, ratios);
    }

    private List<CalculatedShare> allocateByRatios(
            MeterReadingEntity reading,
            List<BillAllocationOverrideRequestDTO> overrides,
            Map<UUID, TenancyEntity> tenancyMap,
            List<BigDecimal> ratios
    ) {
        List<BigDecimal> totals = allocateAmountByRatios(reading.getTotalAmount(), ratios);
        List<BigDecimal> units = allocateAmountByRatios(reading.getUnitsConsumed(), ratios);
        List<BigDecimal> energies = allocateAmountByRatios(reading.getEnergyCharge(), ratios);
        List<BigDecimal> fixeds = allocateAmountByRatios(reading.getFixedChargeSnapshot(), ratios);
        List<BigDecimal> extras = allocateAmountByRatios(reading.getAdditionalCharge(), ratios);
        List<BigDecimal> taxes = allocateAmountByRatios(reading.getTaxAmount(), ratios);
        List<BigDecimal> credits = allocateAmountByRatios(reading.getCreditAmount(), ratios);
        List<BigDecimal> pcts = allocateAmountByRatios(ONE_HUNDRED, ratios);

        return IntStream.range(0, overrides.size())
                .mapToObj(index -> {
                    UUID targetId = overrides.get(index).tenancyId();
                    TenancyEntity tenancy = Optional.ofNullable(tenancyMap.get(targetId))
                            .orElseThrow(() -> new ValidationException("Tenancy does not belong to target meter scope"));
                    return buildShare(tenancy, pcts.get(index), units.get(index), energies.get(index),
                            fixeds.get(index), extras.get(index), taxes.get(index), credits.get(index), totals.get(index));
                })
                .toList();
    }

    private List<BigDecimal> splitAcross(BigDecimal total, BigDecimal countDecimal, int count) {
        BigDecimal base = total.divide(countDecimal, 2, RoundingMode.HALF_UP);
        BigDecimal allocatedBeforeLast = base.multiply(BigDecimal.valueOf(Math.max(0, count - 1)));
        BigDecimal finalShare = total.subtract(allocatedBeforeLast).setScale(2, RoundingMode.HALF_UP);
        return IntStream.range(0, count)
                .mapToObj(index -> index == count - 1 ? finalShare : base)
                .toList();
    }

    private List<BigDecimal> allocateAmountByRatios(BigDecimal total, List<BigDecimal> ratios) {
        int lastIdx = ratios.size() - 1;
        List<BigDecimal> initial = IntStream.range(0, ratios.size())
                .mapToObj(index -> total.multiply(ratios.get(index)).setScale(2, RoundingMode.HALF_UP))
                .toList();
        BigDecimal sumExceptLast = IntStream.range(0, lastIdx)
                .mapToObj(initial::get)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal lastAdjusted = total.subtract(sumExceptLast).setScale(2, RoundingMode.HALF_UP);
        return IntStream.range(0, ratios.size())
                .mapToObj(index -> index == lastIdx ? lastAdjusted : initial.get(index))
                .toList();
    }

    private CalculatedShare buildShare(
            TenancyEntity tenancy,
            BigDecimal sharePct,
            BigDecimal units,
            BigDecimal energy,
            BigDecimal fixed,
            BigDecimal extra,
            BigDecimal tax,
            BigDecimal credit,
            BigDecimal total
    ) {
        return new CalculatedShare(tenancy, sharePct, units, energy, fixed, extra, tax, credit, total);
    }

    public record CalculatedShare(
            TenancyEntity tenancy,
            BigDecimal sharePercentage,
            BigDecimal unitsAllocated,
            BigDecimal energyCharge,
            BigDecimal fixedChargeShare,
            BigDecimal additionalChargeShare,
            BigDecimal taxAmountShare,
            BigDecimal creditShare,
            BigDecimal totalAmount
    ) {}
}

