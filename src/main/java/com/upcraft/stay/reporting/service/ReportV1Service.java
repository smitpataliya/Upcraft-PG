package com.upcraft.stay.reporting.service;

import java.time.LocalDate;
import java.util.UUID;

import com.upcraft.stay.reporting.dto.ReportResponseDTO;
import com.upcraft.stay.reporting.enums.ExportFormat;
import com.upcraft.stay.reporting.enums.ReportType;

/**
 * Service contract for filtered analytical reports and CSV/PDF document exports.
 */
public interface ReportV1Service {

    ReportResponseDTO getReport(
            String principalEmail,
            ReportType reportType,
            UUID propertyId,
            LocalDate fromDate,
            LocalDate toDate,
            int page,
            Integer size
    );

    byte[] exportReport(
            String principalEmail,
            ReportType reportType,
            ExportFormat format,
            UUID propertyId,
            LocalDate fromDate,
            LocalDate toDate
    );

    byte[] exportInvoicePdf(String principalEmail, UUID invoiceId);

    byte[] exportPaymentReceiptPdf(String principalEmail, UUID paymentId);

    byte[] exportDepositStatementPdf(String principalEmail, UUID tenancyId);
}

