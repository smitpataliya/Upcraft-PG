package com.upcraft.stay.audit;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import tools.jackson.databind.ObjectMapper;

import com.upcraft.stay.audit.repository.AuditLogRepository;
import com.upcraft.stay.audit.service.impl.AuditLogServiceImpl;
import com.upcraft.stay.audit.service.impl.AuditSanitizer;
import com.upcraft.stay.billing.repository.InvoiceRepository;
import com.upcraft.stay.billing.repository.PaymentRepository;
import com.upcraft.stay.common.config.AppProperties;
import com.upcraft.stay.common.exception.ValidationException;
import com.upcraft.stay.domain.entity.User;
import com.upcraft.stay.domain.enums.Role;
import com.upcraft.stay.organization.entity.OrganizationEntity;
import com.upcraft.stay.repository.ComplaintRepository;
import com.upcraft.stay.room.repository.BedRepository;
import com.upcraft.stay.search.dto.GlobalSearchResponseDTO;
import com.upcraft.stay.search.service.impl.GlobalSearchServiceImpl;
import com.upcraft.stay.security.AccessGuardService;
import com.upcraft.stay.tenant.repository.TenancyRepository;
import com.upcraft.stay.tenant.repository.TenantRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests verifying audit secret sanitization, pagination/date guards, and resident search boundaries.
 */
class AuditAndSearchUnitTest {

    @Test
    void auditSanitizer_removesSensitiveKeysFromMetadataAndPreservesSafeFields() {
        AuditSanitizer sanitizer = new AuditSanitizer(new ObjectMapper());
        String json = sanitizer.toSafeJson(Map.of(
                "password", "Secret123!",
                "otpCode", "998877",
                "jwtToken", "eyJ...",
                "invoiceNumber", "INV-2026-001",
                "amount", "9500.00"
        ));
        assertThat(json).isNotNull();
        Map<String, String> parsed = sanitizer.parseSafeJson(json);
        assertThat(parsed)
                .containsEntry("invoiceNumber", "INV-2026-001")
                .containsEntry("amount", "9500.00")
                .doesNotContainKeys("password", "otpCode", "jwtToken");
    }

    @Test
    void globalSearch_rejectsShortQueriesAndRestrictsMembersFromStaffTenantDirectory() {
        AccessGuardService accessGuard = mock(AccessGuardService.class);
        TenantRepository tenantRepo = mock(TenantRepository.class);
        BedRepository bedRepo = mock(BedRepository.class);
        TenancyRepository tenancyRepo = mock(TenancyRepository.class);
        InvoiceRepository invoiceRepo = mock(InvoiceRepository.class);
        PaymentRepository paymentRepo = mock(PaymentRepository.class);
        ComplaintRepository complaintRepo = mock(ComplaintRepository.class);

        GlobalSearchServiceImpl service = new GlobalSearchServiceImpl(
                accessGuard, tenantRepo, bedRepo, tenancyRepo, invoiceRepo, paymentRepo, complaintRepo
        );

        assertThatThrownBy(() -> service.search("member@upcraft.test", "a", null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("between 2 and 100");

        User member = User.builder().id(UUID.randomUUID()).email("member@upcraft.test").role(Role.PG_MEMBER).build();
        OrganizationEntity org = new OrganizationEntity();
        org.setId(UUID.randomUUID());

        when(accessGuard.loadActiveUser("member@upcraft.test")).thenReturn(member);
        when(accessGuard.resolveUserOrganization(member)).thenReturn(org);
        when(tenancyRepo.findSelfResidencyTenancies(member.getId(), org.getId(), "member@upcraft.test"))
                .thenReturn(List.of());
        when(invoiceRepo.searchSelfInvoices(eq(member.getId()), eq(org.getId()), eq("member@upcraft.test"), any(), any(Pageable.class)))
                .thenReturn(Page.empty());
        when(paymentRepo.searchSelfPayments(eq(member.getId()), eq(org.getId()), eq("member@upcraft.test"), any(), any(Pageable.class)))
                .thenReturn(Page.empty());
        when(complaintRepo.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(Page.empty());

        GlobalSearchResponseDTO response = service.search("member@upcraft.test", "101", null);
        assertThat(response.tenants()).isEmpty();
        verifyNoInteractions(tenantRepo);
    }

    @Test
    void auditLogService_whenSizeExceedsLimit_rejectsRequest() {
        AccessGuardService accessGuard = mock(AccessGuardService.class);
        AuditLogRepository auditRepo = mock(AuditLogRepository.class);
        AppProperties props = new AppProperties();
        AuditLogServiceImpl service = new AuditLogServiceImpl(
                auditRepo, accessGuard, new AuditSanitizer(new ObjectMapper()), props
        );

        User owner = User.builder().id(UUID.randomUUID()).email("owner@upcraft.test").role(Role.PG_OWNER).build();
        OrganizationEntity org = new OrganizationEntity();
        org.setId(UUID.randomUUID());
        when(accessGuard.loadActiveUser("owner@upcraft.test")).thenReturn(owner);
        when(accessGuard.resolveUserOrganization(owner)).thenReturn(org);
        when(accessGuard.resolveAccessiblePropertyIds(eq(owner), any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.listAuditLogs("owner@upcraft.test", null, null, null, null, null, 0, 500))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("100");
    }
}

