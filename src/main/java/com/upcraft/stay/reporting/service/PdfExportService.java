package com.upcraft.stay.reporting.service;

import java.util.List;

import com.upcraft.stay.billing.dto.DepositAccountSummaryResponseDTO;
import com.upcraft.stay.billing.dto.DepositTransactionResponseDTO;
import com.upcraft.stay.billing.dto.InvoiceDetailResponseDTO;
import com.upcraft.stay.billing.dto.PaymentReceiptResponseDTO;
import com.upcraft.stay.reporting.dto.PublicShareViewResponseDTO;
import com.upcraft.stay.reporting.dto.ReportResponseDTO;

/**
 * Generates styled OpenPDF documents for reports, invoices, payment receipts, deposit statements, and shared vacancy views.
 */
public interface PdfExportService {

    byte[] exportReportPdf(ReportResponseDTO report);

    byte[] exportInvoicePdf(String organizationName, InvoiceDetailResponseDTO invoice, String generatedBy);

    byte[] exportReceiptPdf(String organizationName, PaymentReceiptResponseDTO receipt, String generatedBy);

    byte[] exportDepositStatementPdf(
            String organizationName,
            DepositAccountSummaryResponseDTO summary,
            List<DepositTransactionResponseDTO> transactions,
            String generatedBy
    );

    byte[] exportPublicSharePdf(PublicShareViewResponseDTO shareView);
}

