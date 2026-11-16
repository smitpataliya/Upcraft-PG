package com.upcraft.stay.reporting.service.impl;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import com.upcraft.stay.common.config.AppProperties;
import com.upcraft.stay.common.exception.ValidationException;
import com.upcraft.stay.domain.entity.User;
import com.upcraft.stay.organization.entity.OrganizationEntity;
import com.upcraft.stay.property.entity.PropertyEntity;
import com.upcraft.stay.property.repository.PropertyRepository;
import com.upcraft.stay.reporting.dto.ReportResponseDTO;
import com.upcraft.stay.reporting.enums.ReportType;

/**
 * Shared pagination, IST formatting, date range validation, and payload assembly for reports.
 */
@Component
@RequiredArgsConstructor
public class ReportSupport {

    private static final DateTimeFormatter IST_DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm 'IST'");

    private final PropertyRepository propertyRepository;
    private final AppProperties appProperties;

    public ZoneId zoneId() {
        return ZoneId.of(appProperties.getTimezone());
    }

    public String currentIstTimestamp() {
        return Instant.now().atZone(zoneId()).format(IST_DATE_TIME_FORMAT);
    }

    public String formatInstantIst(Instant instantValue) {
        return Optional.ofNullable(instantValue)
                .map(value -> value.atZone(zoneId()).format(IST_DATE_TIME_FORMAT))
                .orElse("-");
    }

    public void validateDateRange(LocalDate fromDate, LocalDate toDate) {
        if (Optional.ofNullable(fromDate).isPresent()
                && Optional.ofNullable(toDate).isPresent()
                && fromDate.isAfter(toDate)) {
            throw new ValidationException("fromDate cannot be after toDate");
        }
    }

    public Pageable buildReportPageable(int page, Integer requestedSize, boolean exportAll, Sort sort) {
        if (exportAll) {
            return PageRequest.of(0, appProperties.getReports().getMaxExportRows(), sort);
        }
        if (page < 0) {
            throw new ValidationException("Page index must be zero or positive");
        }
        int resolvedSize = Optional.ofNullable(requestedSize)
                .filter(sizeValue -> sizeValue > 0)
                .orElse(appProperties.getPagination().getDefaultSize());
        int maxSize = appProperties.getPagination().getMaxSize();
        if (resolvedSize > maxSize) {
            throw new ValidationException(String.format("Page size cannot exceed %d", maxSize));
        }
        return PageRequest.of(page, resolvedSize, sort);
    }

    public String resolveScopeLabel(UUID propertyId, List<UUID> propertyIds) {
        if (Optional.ofNullable(propertyId).isPresent()) {
            return propertyRepository.findById(propertyId)
                    .map(PropertyEntity::getName)
                    .orElse("Selected Property");
        }
        return CollectionUtils.isEmpty(propertyIds)
                ? "No Properties"
                : String.format("All Accessible Properties (%d)", propertyIds.size());
    }

    public ReportResponseDTO buildResponse(
            ReportType reportType,
            String title,
            OrganizationEntity organization,
            String propertyScopeLabel,
            LocalDate fromDate,
            LocalDate toDate,
            User actorUser,
            List<String> columns,
            List<Map<String, Object>> rows,
            Map<String, BigDecimal> totals,
            Page<?> sourcePage
    ) {
        String actorName = StringUtils.defaultIfBlank(actorUser.getName(), actorUser.getEmail());
        return new ReportResponseDTO(
                reportType,
                title,
                organization.getName(),
                propertyScopeLabel,
                fromDate,
                toDate,
                actorName,
                currentIstTimestamp(),
                columns,
                rows,
                totals,
                sourcePage.getNumber(),
                sourcePage.getSize(),
                sourcePage.getTotalElements(),
                sourcePage.getTotalPages()
        );
    }

    public boolean withinRange(LocalDate candidateDate, LocalDate fromDate, LocalDate toDate) {
        if (Optional.ofNullable(candidateDate).isEmpty()) {
            return false;
        }
        boolean afterFrom = Optional.ofNullable(fromDate).map(start -> !candidateDate.isBefore(start)).orElse(true);
        boolean beforeTo = Optional.ofNullable(toDate).map(end -> !candidateDate.isAfter(end)).orElse(true);
        return afterFrom && beforeTo;
    }

    public BigDecimal safeAmount(BigDecimal value) {
        return Optional.ofNullable(value).orElse(BigDecimal.ZERO);
    }
}

