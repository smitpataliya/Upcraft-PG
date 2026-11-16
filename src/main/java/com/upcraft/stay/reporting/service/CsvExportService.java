package com.upcraft.stay.reporting.service;

import com.upcraft.stay.reporting.dto.ReportResponseDTO;

/**
 * Streams tabular PG reports to RFC-4180 CSV bytes with organization/property metadata and totals.
 */
public interface CsvExportService {

    byte[] exportReportCsv(ReportResponseDTO report);
}

