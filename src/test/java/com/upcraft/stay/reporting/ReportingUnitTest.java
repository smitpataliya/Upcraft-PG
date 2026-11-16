package com.upcraft.stay.reporting;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import com.upcraft.stay.audit.service.AuditLogService;
import com.upcraft.stay.billing.dto.DepositAccountSummaryResponseDTO;
import com.upcraft.stay.billing.dto.DepositTransactionResponseDTO;
import com.upcraft.stay.billing.dto.InvoiceDetailResponseDTO;
import com.upcraft.stay.billing.dto.InvoiceItemResponseDTO;
import com.upcraft.stay.billing.dto.PaymentReceiptResponseDTO;
import com.upcraft.stay.billing.repository.DepositTransactionRepository;
import com.upcraft.stay.billing.repository.InvoiceItemRepository;
import com.upcraft.stay.billing.repository.InvoiceRepository;
import com.upcraft.stay.billing.repository.PaymentRepository;
import com.upcraft.stay.billing.service.DepositService;
import com.upcraft.stay.billing.service.InvoiceService;
import com.upcraft.stay.billing.service.PaymentService;
import com.upcraft.stay.common.config.AppProperties;
import com.upcraft.stay.common.dto.PageResponseDTO;
import com.upcraft.stay.common.enums.BedStatus;
import com.upcraft.stay.common.enums.DepositTransactionType;
import com.upcraft.stay.common.enums.InvoiceItemType;
import com.upcraft.stay.common.enums.InvoiceStatus;
import com.upcraft.stay.common.enums.InvoiceType;
import com.upcraft.stay.common.enums.PaymentMethod;
import com.upcraft.stay.common.enums.PaymentStatus;
import com.upcraft.stay.common.enums.RefundStatus;
import com.upcraft.stay.common.exception.ValidationException;
import com.upcraft.stay.common.util.HashUtils;
import com.upcraft.stay.domain.entity.Room;
import com.upcraft.stay.domain.entity.User;
import com.upcraft.stay.domain.enums.Role;
import com.upcraft.stay.organization.entity.OrganizationEntity;
import com.upcraft.stay.property.entity.PropertyEntity;
import com.upcraft.stay.property.repository.PropertyRepository;
import com.upcraft.stay.reporting.dto.CreateShareLinkRequestDTO;
import com.upcraft.stay.reporting.dto.PublicShareViewResponseDTO;
import com.upcraft.stay.reporting.dto.ReportResponseDTO;
import com.upcraft.stay.reporting.dto.ShareLinkResponseDTO;
import com.upcraft.stay.reporting.entity.ShareLinkEntity;
import com.upcraft.stay.reporting.enums.ReportType;
import com.upcraft.stay.reporting.enums.ShareResourceType;
import com.upcraft.stay.reporting.repository.ShareLinkRepository;
import com.upcraft.stay.reporting.service.impl.CsvExportServiceImpl;
import com.upcraft.stay.reporting.service.impl.PdfExportServiceImpl;
import com.upcraft.stay.reporting.service.impl.ReportSupport;
import com.upcraft.stay.reporting.service.impl.ShareLinkV1ServiceImpl;
import com.upcraft.stay.room.entity.BedEntity;
import com.upcraft.stay.room.repository.BedRepository;
import com.upcraft.stay.security.AccessGuardService;
import com.upcraft.stay.tenant.repository.TenancyRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for CSV/PDF streaming exporters and hashed expiring share-link rules.
 */
class ReportingUnitTest {

    @Test
    void csvAndPdfExporters_includeOrgHeaderTotalsAndValidPdfSignature() {
        CsvExportServiceImpl csvExporter = new CsvExportServiceImpl();
        PdfExportServiceImpl pdfExporter = new PdfExportServiceImpl();

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("tenant", "Aarav, \"Wing A\"");
        row.put("paid", new BigDecimal("5000.00"));
        ReportResponseDTO report = new ReportResponseDTO(
                ReportType.RENT_COLLECTION,
                "Rent Collection Report",
                "Upcraft Stay Group",
                "Wing A",
                LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 30),
                "Owner User",
                "12 Nov 2026, 16:00 IST",
                List.of("tenant", "paid"),
                List.of(row),
                Map.of("totalCollected", new BigDecimal("5000.00")),
                0,
                10,
                1L,
                1
        );

        String csvText = new String(csvExporter.exportReportCsv(report), StandardCharsets.UTF_8);
        assertThat(csvText).contains("Upcraft Stay Group", "Rent Collection Report", "\"Aarav, \"\"Wing A\"\"\"", "Totals\ntotalCollected,5000.00");

        byte[] reportPdf = pdfExporter.exportReportPdf(report);
        assertThat(new String(reportPdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");

        InvoiceDetailResponseDTO invoiceDto = new InvoiceDetailResponseDTO(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Wing A",
                UUID.randomUUID(), "Aarav", "9800000001", UUID.randomUUID(),
                "101", "101-A", "INV-2026-0001", InvoiceType.MONTHLY_RENT, 2026, 11,
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30),
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 5),
                new BigDecimal("9000.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("9000.00"), new BigDecimal("4000.00"), new BigDecimal("5000.00"),
                InvoiceStatus.PARTIALLY_PAID, null, null, Instant.now(),
                List.of(new InvoiceItemResponseDTO(UUID.randomUUID(), InvoiceItemType.RENT, "Monthly Rent", BigDecimal.ONE, new BigDecimal("9000.00"), new BigDecimal("9000.00"), 0)),
                List.of(),
                List.of()
        );
        byte[] invoicePdf = pdfExporter.exportInvoicePdf("Upcraft Stay Group", invoiceDto, "Owner User");
        assertThat(new String(invoicePdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");

        PaymentReceiptResponseDTO receiptDto = new PaymentReceiptResponseDTO(
                UUID.randomUUID(), "RCPT-2026-0001", "Upcraft Stay Group", "Wing A", "Pune",
                "Aarav", "9800000001", "101", "101-A", "INV-2026-0001", 2026, 11,
                Instant.now(), PaymentMethod.UPI, PaymentStatus.COMPLETED, "UPI123",
                new BigDecimal("4000.00"), new BigDecimal("9000.00"), new BigDecimal("4000.00"),
                new BigDecimal("5000.00"), "Owner User", "Part 1"
        );
        byte[] receiptPdf = pdfExporter.exportReceiptPdf("Upcraft Stay Group", receiptDto, "Owner User");
        assertThat(new String(receiptPdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");

        DepositAccountSummaryResponseDTO depositDto = new DepositAccountSummaryResponseDTO(
                UUID.randomUUID(), UUID.randomUUID(), "Wing A", UUID.randomUUID(), "Aarav", "101", "101-A",
                new BigDecimal("15000.00"), new BigDecimal("15000.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, new BigDecimal("15000.00"), BigDecimal.ZERO, RefundStatus.NONE,
                new PageResponseDTO<>(List.of(new DepositTransactionResponseDTO(
                        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                        DepositTransactionType.COLLECTED, new BigDecimal("15000.00"),
                        LocalDate.of(2026, 11, 1), PaymentMethod.UPI, "DEP-1", "Move-in deposit", "Owner User", Instant.now()
                )), 1L, 1, 0, 10, true)
        );
        byte[] depositPdf = pdfExporter.exportDepositStatementPdf("Upcraft Stay Group", depositDto, depositDto.transactions().content(), "Owner User");
        assertThat(new String(depositPdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    @Test
    void shareLinkService_hashesTokenEnforcesExpiryRevocationAndOmitsTenantPiiForVacancy() {
        AccessGuardService accessGuard = mock(AccessGuardService.class);
        ShareLinkRepository shareRepo = mock(ShareLinkRepository.class);
        PropertyRepository propertyRepo = mock(PropertyRepository.class);
        BedRepository bedRepo = mock(BedRepository.class);
        AppProperties appProperties = new AppProperties();

        ShareLinkV1ServiceImpl service = new ShareLinkV1ServiceImpl(
                accessGuard,
                shareRepo,
                propertyRepo,
                mock(InvoiceService.class),
                mock(PaymentService.class),
                mock(DepositService.class),
                mock(InvoiceRepository.class),
                mock(InvoiceItemRepository.class),
                mock(PaymentRepository.class),
                mock(TenancyRepository.class),
                mock(DepositTransactionRepository.class),
                bedRepo,
                new PdfExportServiceImpl(),
                appProperties,
                new ReportSupport(propertyRepo, appProperties),
                mock(AuditLogService.class)
        );

        UUID orgId = UUID.randomUUID();
        UUID propertyId = UUID.randomUUID();
        User owner = User.builder().id(UUID.randomUUID()).email("owner@upcraft.test").name("Owner").role(Role.PG_OWNER).build();
        OrganizationEntity org = new OrganizationEntity();
        org.setId(orgId);
        org.setName("Upcraft Stay");
        PropertyEntity property = new PropertyEntity();
        property.setId(propertyId);
        property.setOrganization(org);
        property.setName("Wing A");
        property.setAddressLine("12 Main Rd");
        property.setCity("Pune");
        property.setState("Maharashtra");

        when(accessGuard.loadActiveUser("owner@upcraft.test")).thenReturn(owner);
        when(accessGuard.resolveUserOrganization(owner)).thenReturn(org);
        when(accessGuard.requirePropertyManagementAccess(owner, propertyId, "READ")).thenReturn(property);
        when(shareRepo.save(any(ShareLinkEntity.class))).thenAnswer(inv -> {
            ShareLinkEntity entity = inv.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(UUID.randomUUID());
            }
            return entity;
        });

        ShareLinkResponseDTO created = service.createShareLink(
                "owner@upcraft.test",
                new CreateShareLinkRequestDTO(ShareResourceType.PUBLIC_VACANCY, propertyId, 24)
        );
        assertThat(created.shareUrl()).contains("http://localhost:3000/share/");
        assertThat(created.whatsappShareUrl()).startsWith("https://wa.me/?text=");
        verify(shareRepo).save(any(ShareLinkEntity.class));

        String rawToken = created.shareUrl().substring(created.shareUrl().lastIndexOf('/') + 1);
        ShareLinkEntity storedEntity = new ShareLinkEntity();
        storedEntity.setId(created.id());
        storedEntity.setOrganization(org);
        storedEntity.setProperty(property);
        storedEntity.setCreatedByUser(owner);
        storedEntity.setResourceType(ShareResourceType.PUBLIC_VACANCY);
        storedEntity.setResourceId(propertyId);
        storedEntity.setTokenHash(HashUtils.sha256Hex(rawToken));
        storedEntity.setExpiresAt(Instant.now().plus(12, ChronoUnit.HOURS));

        Room room = Room.builder().id(UUID.randomUUID()).number("101").roomType("DOUBLE").build();
        BedEntity bed = new BedEntity();
        bed.setId(UUID.randomUUID());
        bed.setProperty(property);
        bed.setRoom(room);
        bed.setBedCode("101-A");
        bed.setMonthlyRent(new BigDecimal("9500.00"));
        bed.setDefaultDeposit(new BigDecimal("15000.00"));
        bed.setBedStatus(BedStatus.VACANT);

        when(shareRepo.findByTokenHash(HashUtils.sha256Hex(rawToken))).thenReturn(Optional.of(storedEntity));
        when(propertyRepo.findById(propertyId)).thenReturn(Optional.of(property));
        when(bedRepo.findAvailability(
                eq(List.of(propertyId)), eq(true), eq(BedStatus.VACANT),
                anyBoolean(), any(), anyBoolean(), any(), anyBoolean(), any(), any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(bed)));

        PublicShareViewResponseDTO view = service.getPublicShareView(rawToken);
        assertThat(view.resourceType()).isEqualTo(ShareResourceType.PUBLIC_VACANCY);
        assertThat(view.lineItems()).hasSize(1);
        assertThat(view.lineItems().get(0)).containsKeys("roomNumber", "bedCode", "monthlyRent");
        assertThat(view.lineItems().get(0)).doesNotContainKeys("tenantName", "phone", "email");

        storedEntity.setExpiresAt(Instant.now().minus(1, ChronoUnit.HOURS));
        assertThatThrownBy(() -> service.getPublicShareView(rawToken))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("expired");

        storedEntity.setExpiresAt(Instant.now().plus(12, ChronoUnit.HOURS));
        storedEntity.setRevokedAt(Instant.now());
        assertThatThrownBy(() -> service.getPublicShareView(rawToken))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("revoked");
    }
}
