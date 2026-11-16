package com.upcraft.stay.reporting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.upcraft.stay.reporting.enums.ReportType;

/**
 * Structured paginated report payload including organization header, IST generation time, and totals.
 */
public record ReportResponseDTO(
        ReportType reportType,
        String title,
        String organizationName,
        String propertyScopeLabel,
        LocalDate fromDate,
        LocalDate toDate,
        String generatedBy,
        String generatedAtIst,
        List<String> columns,
        List<Map<String, Object>> rows,
        Map<String, BigDecimal> totals,
        int page,
        int pageSize,
        long totalElements,
        int totalPages
) {
}

