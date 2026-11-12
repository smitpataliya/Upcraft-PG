package com.upcraft.stay.common.filter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

import com.upcraft.stay.common.config.AppProperties;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitAndMigrationSafetyTest {

    @Test
    void rateLimitFilter_whenExceedingAuthThreshold_returns429WithRetryAfter() throws Exception {
        AppProperties appProperties = new AppProperties();
        appProperties.getRateLimit().setAuthRequestsPerMinute(2);
        RateLimitFilter filter = new RateLimitFilter(appProperties, new ObjectMapper());

        for (int attempt = 0; attempt < 2; attempt++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
            request.setRemoteAddr("10.10.10.5");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
            assertThat(response.getStatus()).isEqualTo(200);
        }

        MockHttpServletRequest blockedRequest = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        blockedRequest.setRemoteAddr("10.10.10.5");
        MockHttpServletResponse blockedResponse = new MockHttpServletResponse();
        filter.doFilter(blockedRequest, blockedResponse, new MockFilterChain());

        assertThat(blockedResponse.getStatus()).isEqualTo(429);
        assertThat(blockedResponse.getHeader("Retry-After")).isEqualTo("60");
        assertThat(blockedResponse.getContentAsString()).contains("RATE_LIMIT_EXCEEDED");
    }

    @Test
    void flywayMigrations_v1ThroughV4_followSafeNonDestructiveAndAuditRules() throws Exception {
        Path migrationDir = Path.of("src/main/resources/db/migration");
        List<String> expectedFiles = List.of(
                "V1__baseline_existing_schema.sql",
                "V2__add_audit_columns_to_existing_tables.sql",
                "V3__create_organizations_properties_invitations.sql",
                "V4__backfill_default_org_and_property.sql",
                "V5__create_floors_table.sql",
                "V6__upgrade_rooms_and_create_beds_table.sql",
                "V7__create_tenants_and_tenancies_tables.sql",
                "V8__backfill_rooms_beds_tenancies.sql",
                "V9__create_invoices_and_payments_tables.sql",
                "V10__create_deposit_transactions_and_backfill.sql",
                "V11__create_storage_and_electricity_tables.sql",
                "V12__fix_invoice_items_version_and_boolean_columns.sql"
        );
        for (String fileName : expectedFiles) {
            Path sqlPath = migrationDir.resolve(fileName);
            assertThat(Files.exists(sqlPath)).isTrue();
            String sqlContent = Files.readString(sqlPath).toUpperCase();
            assertThat(sqlContent).doesNotContain("DROP TABLE");
            assertThat(sqlContent).doesNotContain("DROP DATABASE");
            assertThat(sqlContent).doesNotContain("TRUNCATE");
        }

        for (String tableSqlFile : List.of(
                "V3__create_organizations_properties_invitations.sql",
                "V5__create_floors_table.sql",
                "V6__upgrade_rooms_and_create_beds_table.sql",
                "V7__create_tenants_and_tenancies_tables.sql",
                "V9__create_invoices_and_payments_tables.sql",
                "V10__create_deposit_transactions_and_backfill.sql",
                "V11__create_storage_and_electricity_tables.sql"
        )) {
            String sql = Files.readString(migrationDir.resolve(tableSqlFile));
            assertThat(sql).contains("created_at", "created_by", "updated_at", "updated_by",
                    "deleted_at", "deleted_by", "status", "audit_tracker_id");
        }
        String v3Sql = Files.readString(migrationDir.resolve("V3__create_organizations_properties_invitations.sql"));
        assertThat(v3Sql).contains("active_guard TINYINT GENERATED ALWAYS AS");
        String v7Sql = Files.readString(migrationDir.resolve("V7__create_tenants_and_tenancies_tables.sql"));
        assertThat(v7Sql).contains("active_bed_guard BINARY(16) GENERATED ALWAYS AS");
        assertThat(v7Sql).contains("UNIQUE KEY uk_tenancies_active_bed (active_bed_guard)");
    }
}
