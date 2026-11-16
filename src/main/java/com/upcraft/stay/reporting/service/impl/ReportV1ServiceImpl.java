package com.upcraft.stay.reporting.service.impl;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.upcraft.stay.billing.dto.DepositAccountSummaryResponseDTO;
import com.upcraft.stay.billing.dto.DepositTransactionResponseDTO;
import com.upcraft.stay.billing.dto.InvoiceDetailResponseDTO;
import com.upcraft.stay.billing.dto.PaymentReceiptResponseDTO;
import com.upcraft.stay.billing.service.DepositService;
import com.upcraft.stay.billing.service.InvoiceService;
import com.upcraft.stay.billing.service.PaymentService;
import com.upcraft.stay.common.config.AppProperties;
import com.upcraft.stay.domain.entity.User;
import com.upcraft.stay.organization.entity.OrganizationEntity;
import com.upcraft.stay.reporting.dto.ReportResponseDTO;
import com.upcraft.stay.reporting.enums.ExportFormat;
import com.upcraft.stay.reporting.enums.ReportType;
import com.upcraft.stay.reporting.service.CsvExportService;
import com.upcraft.stay.reporting.service.PdfExportService;
import com.upcraft.stay.reporting.service.ReportV1Service;
import com.upcraft.stay.security.AccessGuardService;

/**
 * Orchestrates filtered analytical reports, CSV/PDF streaming exports, and invoice/receipt/deposit PDFs.
 */
@Service
@RequiredArgsConstructor
public class ReportV1ServiceImpl implements ReportV1Service {

    private final AccessGuardService accessGuardService;
    private final FinancialReportBuilder financialReportBuilder;
    private final OperationsReportBuilder operationsReportBuilder;
    private final CsvExportService csvExportService;
    private final PdfExportService pdfExportService;
    private final InvoiceService invoiceService;
    private final PaymentService paymentService;
    private final DepositService depositService;
    private final ReportSupport support;
    private final AppProperties appProperties;

    @Override
    @Transactional(readOnly = true)
    public ReportResponseDTO getReport(
            String principalEmail,
            ReportType reportType,
            UUID propertyId,
            LocalDate fromDate,
            LocalDate toDate,
            int page,
            Integer size
    ) {
        return buildReport(principalEmail, reportType, propertyId, fromDate, toDate, page, size, false);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] exportReport(
            String principalEmail,
            ReportType reportType,
            ExportFormat format,
            UUID propertyId,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        ReportResponseDTO report = buildReport(
                principalEmail, reportType, propertyId, fromDate, toDate, 0, null, true
        );
        return switch (format) {
            case CSV -> csvExportService.exportReportCsv(report);
            case PDF -> pdfExportService.exportReportPdf(report);
        };
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] exportInvoicePdf(String principalEmail, UUID invoiceId) {
        User actorUser = accessGuardService.loadActiveUser(principalEmail);
        OrganizationEntity organization = accessGuardService.resolveUserOrganization(actorUser);
        InvoiceDetailResponseDTO detail = invoiceService.getInvoiceDetail(principalEmail, invoiceId);
        return pdfExportService.exportInvoicePdf(
                organization.getName(), detail, StringUtils.defaultIfBlank(actorUser.getName(), actorUser.getEmail())
        );
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] exportPaymentReceiptPdf(String principalEmail, UUID paymentId) {
        User actorUser = accessGuardService.loadActiveUser(principalEmail);
        OrganizationEntity organization = accessGuardService.resolveUserOrganization(actorUser);
        PaymentReceiptResponseDTO receipt = paymentService.getPaymentReceipt(principalEmail, paymentId);
        return pdfExportService.exportReceiptPdf(
                organization.getName(), receipt, StringUtils.defaultIfBlank(actorUser.getName(), actorUser.getEmail())
        );
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] exportDepositStatementPdf(String principalEmail, UUID tenancyId) {
        User actorUser = accessGuardService.loadActiveUser(principalEmail);
        OrganizationEntity organization = accessGuardService.resolveUserOrganization(actorUser);
        DepositAccountSummaryResponseDTO summary = depositService.getTenancyDepositSummary(
                principalEmail, tenancyId, 0, appProperties.getPagination().getMaxSize()
        );
        List<DepositTransactionResponseDTO> transactions = summary.transactions().content();
        return pdfExportService.exportDepositStatementPdf(
                organization.getName(),
                summary,
                transactions,
                StringUtils.defaultIfBlank(actorUser.getName(), actorUser.getEmail())
        );
    }

    private ReportResponseDTO buildReport(
            String principalEmail,
            ReportType reportType,
            UUID propertyId,
            LocalDate fromDate,
            LocalDate toDate,
            int page,
            Integer size,
            boolean exportAll
    ) {
        support.validateDateRange(fromDate, toDate);
        User actorUser = accessGuardService.loadActiveUser(principalEmail);
        OrganizationEntity organization = accessGuardService.resolveUserOrganization(actorUser);
        List<UUID> propertyIds = accessGuardService.resolveAccessiblePropertyIds(
                actorUser, Optional.ofNullable(propertyId)
        );
        String scopeLabel = support.resolveScopeLabel(propertyId, propertyIds);
        if (CollectionUtils.isEmpty(propertyIds)) {
            return new ReportResponseDTO(
                    reportType,
                    reportType.name(),
                    organization.getName(),
                    scopeLabel,
                    fromDate,
                    toDate,
                    StringUtils.defaultIfBlank(actorUser.getName(), actorUser.getEmail()),
                    support.currentIstTimestamp(),
                    List.of(),
                    List.of(),
                    Map.of(),
                    0,
                    appProperties.getPagination().getDefaultSize(),
                    0L,
                    0
            );
        }

        return switch (reportType) {
            case RENT_COLLECTION -> financialReportBuilder.buildRentCollection(
                    organization, scopeLabel, propertyIds, fromDate, toDate, actorUser, page, size, exportAll);
            case OUTSTANDING -> financialReportBuilder.buildOutstanding(
                    organization, scopeLabel, propertyIds, fromDate, toDate, actorUser, page, size, exportAll);
            case PAYMENTS -> financialReportBuilder.buildPayments(
                    organization, scopeLabel, propertyIds, fromDate, toDate, actorUser, page, size, exportAll);
            case DEPOSITS -> financialReportBuilder.buildDeposits(
                    organization, scopeLabel, propertyIds, fromDate, toDate, actorUser, page, size, exportAll);
            case ELECTRICITY -> financialReportBuilder.buildElectricity(
                    organization, scopeLabel, propertyIds, fromDate, toDate, actorUser, page, size, exportAll);
            case OCCUPANCY -> operationsReportBuilder.buildOccupancy(
                    organization, scopeLabel, propertyIds, fromDate, toDate, actorUser, page, size, exportAll);
            case TENANTS -> operationsReportBuilder.buildTenants(
                    organization, scopeLabel, propertyIds, fromDate, toDate, actorUser, page, size, exportAll);
            case MOVE_IN_OUT -> operationsReportBuilder.buildMoveInOut(
                    organization, scopeLabel, propertyIds, fromDate, toDate, actorUser, page, size, exportAll);
            case COMPLAINTS -> operationsReportBuilder.buildComplaints(
                    organization, scopeLabel, propertyIds, fromDate, toDate, actorUser, page, size, exportAll);
        };
    }
}

