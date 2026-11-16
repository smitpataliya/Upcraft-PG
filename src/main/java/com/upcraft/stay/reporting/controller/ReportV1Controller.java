package com.upcraft.stay.reporting.controller;

import java.security.Principal;
import java.time.LocalDate;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.upcraft.stay.common.constants.AppConstants;
import com.upcraft.stay.common.dto.ApiResponseDTO;
import com.upcraft.stay.reporting.dto.ReportResponseDTO;
import com.upcraft.stay.reporting.enums.ExportFormat;
import com.upcraft.stay.reporting.enums.ReportType;
import com.upcraft.stay.reporting.service.ReportV1Service;

/**
 * Versioned REST controller for operational/financial reports and PDF/CSV downloads.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ReportV1Controller {

    private final ReportV1Service reportV1Service;

    @GetMapping("/reports/{reportType}")
    @PreAuthorize("hasAnyRole('PG_OWNER', 'MANAGER')")
    public ResponseEntity<ApiResponseDTO<ReportResponseDTO>> getReport(
            Principal principal,
            @PathVariable ReportType reportType,
            @RequestParam(required = false) UUID propertyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size
    ) {
        ReportResponseDTO report = reportV1Service.getReport(
                principal.getName(), reportType, propertyId, fromDate, toDate, page, size
        );
        return ResponseEntity.ok(ApiResponseDTO.success("Report generated", report, traceId()));
    }

    @GetMapping("/reports/{reportType}/export")
    @PreAuthorize("hasAnyRole('PG_OWNER', 'MANAGER')")
    public ResponseEntity<byte[]> exportReport(
            Principal principal,
            @PathVariable ReportType reportType,
            @RequestParam(defaultValue = "CSV") ExportFormat format,
            @RequestParam(required = false) UUID propertyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate
    ) {
        byte[] content = reportV1Service.exportReport(
                principal.getName(), reportType, format, propertyId, fromDate, toDate
        );
        MediaType mediaType = format == ExportFormat.PDF ? MediaType.APPLICATION_PDF : MediaType.parseMediaType("text/csv");
        String extension = format == ExportFormat.PDF ? "pdf" : "csv";
        String filename = String.format("%s-report.%s", reportType.name().toLowerCase(), extension);
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .body(content);
    }

    @GetMapping("/invoices/{invoiceId}/pdf")
    @PreAuthorize("hasAnyRole('PG_OWNER', 'MANAGER', 'PG_MEMBER')")
    public ResponseEntity<byte[]> exportInvoicePdf(Principal principal, @PathVariable UUID invoiceId) {
        byte[] pdfBytes = reportV1Service.exportInvoicePdf(principal.getName(), invoiceId);
        return pdfResponse(pdfBytes, String.format("invoice-%s.pdf", invoiceId));
    }

    @GetMapping("/payments/{paymentId}/receipt-pdf")
    @PreAuthorize("hasAnyRole('PG_OWNER', 'MANAGER', 'PG_MEMBER')")
    public ResponseEntity<byte[]> exportPaymentReceiptPdf(Principal principal, @PathVariable UUID paymentId) {
        byte[] pdfBytes = reportV1Service.exportPaymentReceiptPdf(principal.getName(), paymentId);
        return pdfResponse(pdfBytes, String.format("receipt-%s.pdf", paymentId));
    }

    @GetMapping("/tenancies/{tenancyId}/deposit-statement-pdf")
    @PreAuthorize("hasAnyRole('PG_OWNER', 'MANAGER', 'PG_MEMBER')")
    public ResponseEntity<byte[]> exportDepositStatementPdf(Principal principal, @PathVariable UUID tenancyId) {
        byte[] pdfBytes = reportV1Service.exportDepositStatementPdf(principal.getName(), tenancyId);
        return pdfResponse(pdfBytes, String.format("deposit-statement-%s.pdf", tenancyId));
    }

    private ResponseEntity<byte[]> pdfResponse(byte[] pdfBytes, String filename) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .body(pdfBytes);
    }

    private String traceId() {
        return MDC.get(AppConstants.TRACE_ID_MDC_KEY);
    }
}

