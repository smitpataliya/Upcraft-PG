package com.upcraft.stay.reporting;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import com.upcraft.stay.common.filter.RateLimitFilter;
import com.upcraft.stay.common.filter.TraceIdFilter;
import com.upcraft.stay.reporting.enums.ReportType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end Milestone 6 integration tests covering DB-reconciled dashboards,
 * all 9 report types, CSV/PDF exports, and revocable non-PII public share links.
 */
@SpringBootTest
@ActiveProfiles("test")
class Milestone6ReportsSharingIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TraceIdFilter traceIdFilter;

    @Autowired
    private RateLimitFilter rateLimitFilter;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .addFilters(traceIdFilter, rateLimitFilter)
                .build();
    }

    @Test
    void dashboardsReportsExportsAndShareLinks_reconcileWithRealDbAndEnforceIsolation() throws Exception {
        String ownerToken = registerOwner("M6 Owner", "m6.owner@upcraft.test", "M6 Group", "M6 Wing");
        String otherOwnerToken = registerOwner("Other M6 Owner", "m6.other@upcraft.test", "Other M6 Org", "Other M6 Wing");

        String propertyId = readData(mockMvc.perform(get("/api/v1/properties")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn()).path("content").get(0).path("id").asText();

        JsonNode roomData = readData(mockMvc.perform(post("/api/v1/properties/" + propertyId + "/rooms")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "floorNumber": 1,
                                  "number": "601",
                                  "roomType": "DOUBLE",
                                  "sharing": "2-Sharing",
                                  "capacity": 2,
                                  "baseMonthlyRent": 9500.00,
                                  "defaultDeposit": 15000.00,
                                  "amenities": ["AC"],
                                  "autoCreateBeds": true
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn());
        String bed1Id = roomData.path("beds").get(0).path("id").asText();

        JsonNode tenancyNode = readData(mockMvc.perform(post("/api/v1/tenancies/move-in")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bedId": "%s",
                                  "fullName": "Resident Reports",
                                  "phone": "9876500601",
                                  "email": "resident.m6@upcraft.test",
                                  "moveInDate": "2026-11-01",
                                  "agreedMonthlyRent": 9500.00,
                                  "securityDepositAmount": 15000.00,
                                  "depositReceivedAmount": 15000.00,
                                  "rentDueDay": 5,
                                  "sendPortalInvite": false
                                }
                                """.formatted(bed1Id)))
                .andExpect(status().isCreated())
                .andReturn());
        String tenancyId = tenancyNode.path("id").asText();
        String tenantId = tenancyNode.path("tenantId").asText();

        mockMvc.perform(post("/api/v1/invoices/generate-rent")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "propertyId": "%s", "billingYear": 2026, "billingMonth": 11, "prorateMidMonth": false }
                                """.formatted(propertyId)))
                .andExpect(status().isCreated());

        String invoiceId = readData(mockMvc.perform(get("/api/v1/invoices?tenantId=" + tenantId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn()).path("content").get(0).path("id").asText();

        String paymentId = readData(mockMvc.perform(post("/api/v1/invoices/" + invoiceId + "/payments")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "amount": 4000.00,
                                  "paymentMethod": "UPI",
                                  "referenceNumber": "UPI-M6-001",
                                  "idempotencyKey": "m6-pay-1",
                                  "notes": "Part M6"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn()).path("id").asText();

        // 1. Real DB Owner Dashboard reconciles figures and isolates security deposit from rent revenue
        JsonNode dashboard = readData(mockMvc.perform(get("/api/v1/dashboard/owner?year=2026&month=11")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(dashboard.path("totalBeds").asLong()).isEqualTo(2L);
        assertThat(dashboard.path("occupiedBeds").asLong()).isEqualTo(1L);
        assertThat(dashboard.path("vacantBeds").asLong()).isEqualTo(1L);
        assertThat(dashboard.path("rentCollectedMonth").asDouble()).isEqualTo(4000.0);
        assertThat(dashboard.path("rentOutstanding").asDouble()).isEqualTo(5500.0);
        assertThat(dashboard.path("depositsHeld").asDouble()).isEqualTo(15000.0);

        // 2. All 9 report types return structured paginated responses with IST timestamp
        for (ReportType reportType : ReportType.values()) {
            JsonNode reportNode = readData(mockMvc.perform(get("/api/v1/reports/" + reportType.name())
                            .header("Authorization", "Bearer " + ownerToken))
                    .andExpect(status().isOk())
                    .andReturn());
            assertThat(reportNode.path("organizationName").asText()).isEqualTo("M6 Group");
            assertThat(reportNode.path("generatedAtIst").asText()).contains("IST");
        }

        // 3. CSV & PDF report exports + individual Invoice/Receipt/Deposit PDFs
        MvcResult csvResult = mockMvc.perform(get("/api/v1/reports/RENT_COLLECTION/export?format=CSV")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("text/csv")))
                .andReturn();
        assertThat(csvResult.getResponse().getContentAsString()).contains("M6 Group", "Resident Reports", "4000.00");

        byte[] reportPdf = mockMvc.perform(get("/api/v1/reports/RENT_COLLECTION/export?format=PDF")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(reportPdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");

        byte[] invoicePdf = mockMvc.perform(get("/api/v1/invoices/" + invoiceId + "/pdf")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(invoicePdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");

        byte[] receiptPdf = mockMvc.perform(get("/api/v1/payments/" + paymentId + "/receipt-pdf")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(receiptPdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");

        byte[] depositPdf = mockMvc.perform(get("/api/v1/tenancies/" + tenancyId + "/deposit-statement-pdf")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(depositPdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");

        // 4. Expiring PUBLIC_VACANCY share link exposes vacant beds with zero tenant PII and respects revocation
        JsonNode createdVacancyLink = readData(mockMvc.perform(post("/api/v1/share-links")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "resourceType": "PUBLIC_VACANCY",
                                  "resourceId": "%s",
                                  "expiryHours": 48
                                }
                                """.formatted(propertyId)))
                .andExpect(status().isCreated())
                .andReturn());
        String shareLinkId = createdVacancyLink.path("id").asText();
        String shareUrl = createdVacancyLink.path("shareUrl").asText();
        String rawToken = shareUrl.substring(shareUrl.lastIndexOf('/') + 1);
        assertThat(createdVacancyLink.path("whatsappShareUrl").asText()).startsWith("https://wa.me/?text=");

        MvcResult publicRes = mockMvc.perform(get("/api/v1/public/share/" + rawToken))
                .andExpect(status().isOk())
                .andReturn();
        String publicBody = publicRes.getResponse().getContentAsString();
        assertThat(publicBody).doesNotContain("Resident Reports", "9876500601", "resident.m6@upcraft.test");
        assertThat(readData(publicRes).path("lineItems")).hasSize(1);

        byte[] publicPdf = mockMvc.perform(get("/api/v1/public/share/" + rawToken + "/pdf"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(publicPdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");

        // Cross-org isolation blocks other owner
        mockMvc.perform(get("/api/v1/invoices/" + invoiceId + "/pdf")
                        .header("Authorization", "Bearer " + otherOwnerToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/share-links/" + shareLinkId)
                        .header("Authorization", "Bearer " + otherOwnerToken))
                .andExpect(status().isNotFound());

        // Owner revokes link -> public view fails
        mockMvc.perform(delete("/api/v1/share-links/" + shareLinkId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/public/share/" + rawToken))
                .andExpect(status().isBadRequest());
    }

    private String registerOwner(String name, String email, String orgName, String propName) throws Exception {
        String payload = """
                {
                  "name": "%s",
                  "email": "%s",
                  "phone": "9812340088",
                  "password": "Password@123",
                  "organizationName": "%s",
                  "propertyName": "%s",
                  "propertyType": "PG",
                  "addressLine": "12 Main Rd",
                  "city": "Pune",
                  "state": "Maharashtra",
                  "pinCode": "411001"
                }
                """.formatted(name, email, orgName, propName);
        return readData(mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn()).path("token").asText();
    }

    private JsonNode readData(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }
}

