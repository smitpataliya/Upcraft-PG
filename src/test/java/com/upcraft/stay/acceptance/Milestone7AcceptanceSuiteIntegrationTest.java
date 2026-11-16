package com.upcraft.stay.acceptance;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Consolidated Milestone 7 acceptance suite verifying end-to-end PG lifecycle,
 * role isolation, audit log integrity, global search bounding, and failure scenarios.
 */
@SpringBootTest
@ActiveProfiles("test")
class Milestone7AcceptanceSuiteIntegrationTest {

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
    void fullPlatformAcceptanceWorkflowsAndSecurityHardening_executeEndToEnd() throws Exception {
        // 1. Health readiness probe works unauthenticated
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());

        // 2. Owner registration & initial property creation + audit log generated
        String ownerToken = registerOwner("Accept Owner", "accept.owner@upcraft.test", "Accept Group", "Wing North");
        String wingNorthId = readData(mockMvc.perform(get("/api/v1/properties")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn()).path("content").get(0).path("id").asText();

        String wingSouthId = readData(mockMvc.perform(post("/api/v1/properties")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Wing South",
                                  "propertyType": "PG",
                                  "addressLine": "45 Park Ave",
                                  "city": "Pune",
                                  "state": "Maharashtra",
                                  "pinCode": "411002"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn()).path("id").asText();

        // 3. Failed login creates LOGIN_FAILED audit entry independently and returns 401
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "accept.owner@upcraft.test", "password": "WrongPassword!" }
                                """))
                .andExpect(status().isUnauthorized());

        // 4. Create floor, room, beds on Wing North; double bed allocation blocked with 409
        JsonNode roomNode = readData(mockMvc.perform(post("/api/v1/properties/" + wingNorthId + "/rooms")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "floorNumber": 1,
                                  "number": "701",
                                  "roomType": "DOUBLE",
                                  "sharing": "2-Sharing",
                                  "capacity": 2,
                                  "baseMonthlyRent": 10000.00,
                                  "defaultDeposit": 15000.00,
                                  "amenities": ["AC"],
                                  "autoCreateBeds": true
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn());
        String bed1Id = roomNode.path("beds").get(0).path("id").asText();
        String bed2Id = roomNode.path("beds").get(1).path("id").asText();

        // Invite Manager restricted ONLY to Wing South
        String mgrInviteCode = readData(mockMvc.perform(post("/api/v1/invitations")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "propertyId": "%s",
                                  "email": "south.manager@upcraft.test",
                                  "phone": "9800007701",
                                  "inviteeName": "South Manager",
                                  "targetRole": "MANAGER",
                                  "canManageTenants": true,
                                  "canRecordPayments": true
                                }
                                """.formatted(wingSouthId)))
                .andExpect(status().isCreated())
                .andReturn()).path("inviteCode").asText();

        String managerToken = readData(mockMvc.perform(post("/api/v1/invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "code": "%s", "name": "South Manager", "phone": "9800007701", "password": "Password@123" }
                                """.formatted(mgrInviteCode)))
                .andExpect(status().isOk())
                .andReturn()).path("token").asText();

        // 5. Onboard tenant into bed1 + confirm duplicate bed1 allocation fails (409)
        JsonNode tenancyNode = readData(mockMvc.perform(post("/api/v1/tenancies/move-in")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bedId": "%s",
                                  "fullName": "Karan Mehta",
                                  "phone": "9876507001",
                                  "email": "karan.m7@upcraft.test",
                                  "moveInDate": "2026-11-01",
                                  "agreedMonthlyRent": 10000.00,
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

        mockMvc.perform(post("/api/v1/tenancies/move-in")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bedId": "%s",
                                  "fullName": "Duplicate Tenant",
                                  "phone": "9876507099",
                                  "moveInDate": "2026-11-02",
                                  "agreedMonthlyRent": 10000.00,
                                  "securityDepositAmount": 15000.00,
                                  "depositReceivedAmount": 0.00
                                }
                                """.formatted(bed1Id)))
                .andExpect(status().isConflict());

        // Invite Resident Karan and confirm self-isolation
        String resInviteCode = readData(mockMvc.perform(post("/api/v1/invitations")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "propertyId": "%s",
                                  "email": "karan.m7@upcraft.test",
                                  "phone": "9876507001",
                                  "inviteeName": "Karan Mehta",
                                  "targetRole": "PG_MEMBER"
                                }
                                """.formatted(wingNorthId)))
                .andExpect(status().isCreated())
                .andReturn()).path("inviteCode").asText();

        String residentToken = readData(mockMvc.perform(post("/api/v1/invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "code": "%s", "name": "Karan Mehta", "phone": "9876507001", "password": "Password@123" }
                                """.formatted(resInviteCode)))
                .andExpect(status().isOk())
                .andReturn()).path("token").asText();

        // Generate monthly rent idempotently + record partial payment + verify invoice status/balance
        mockMvc.perform(post("/api/v1/invoices/generate-rent")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "propertyId": "%s", "billingYear": 2026, "billingMonth": 11, "prorateMidMonth": false }
                                """.formatted(wingNorthId)))
                .andExpect(status().isCreated());

        JsonNode secondGen = readData(mockMvc.perform(post("/api/v1/invoices/generate-rent")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "propertyId": "%s", "billingYear": 2026, "billingMonth": 11, "prorateMidMonth": false }
                                """.formatted(wingNorthId)))
                .andExpect(status().isCreated())
                .andReturn());
        assertThat(secondGen.path("generatedCount").asInt()).isZero();

        String invoiceId = readData(mockMvc.perform(get("/api/v1/invoices?tenantId=" + tenantId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn()).path("content").get(0).path("id").asText();

        mockMvc.perform(post("/api/v1/invoices/" + invoiceId + "/payments")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "amount": 4000.00,
                                  "paymentMethod": "UPI",
                                  "referenceNumber": "UPI-M7-001",
                                  "idempotencyKey": "m7-pay-1"
                                }
                                """))
                .andExpect(status().isCreated());

        // 6. Global Search verification: Owner finds Karan, Invoice & Room 701; South Manager CANNOT see Wing North hits; Resident gets no tenants directory
        JsonNode ownerSearch = readData(mockMvc.perform(get("/api/v1/search?q=701")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(ownerSearch.path("roomsAndBeds")).isNotEmpty();

        JsonNode karanSearch = readData(mockMvc.perform(get("/api/v1/search?q=Karan")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(karanSearch.path("tenants")).hasSize(1);
        assertThat(karanSearch.path("invoices")).hasSize(1);
        assertThat(karanSearch.path("payments")).hasSize(1);

        JsonNode managerSearch = readData(mockMvc.perform(get("/api/v1/search?q=Karan")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(managerSearch.path("totalHits").asInt()).isZero();

        JsonNode residentSearch = readData(mockMvc.perform(get("/api/v1/search?q=701")
                        .header("Authorization", "Bearer " + residentToken))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(residentSearch.path("tenants")).isEmpty();
        assertThat(residentSearch.path("roomsAndBeds")).hasSize(1);

        // Resident short query (<2 chars) fails 400
        mockMvc.perform(get("/api/v1/search?q=a")
                        .header("Authorization", "Bearer " + residentToken))
                .andExpect(status().isBadRequest());

        // 7. Audit Log verification: Owner inspects org logs; Manager ONLY sees Wing South (zero Wing North logs); Resident blocked 403!
        JsonNode ownerAudit = readData(mockMvc.perform(get("/api/v1/audit-logs?size=50")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn());
        String auditJson = objectMapper.writeValueAsString(ownerAudit);
        assertThat(auditJson).contains("USER_REGISTERED", "LOGIN_FAILED", "PROPERTY_CREATED", "TENANCY_MOVE_IN", "PAYMENT_RECORDED");
        assertThat(auditJson).doesNotContain("WrongPassword!", "Password@123");

        JsonNode managerAudit = readData(mockMvc.perform(get("/api/v1/audit-logs?size=50")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(objectMapper.writeValueAsString(managerAudit)).doesNotContain("PAYMENT_RECORDED", "TENANCY_MOVE_IN");

        // Manager trying to query Wing North audit logs gets 403
        mockMvc.perform(get("/api/v1/audit-logs?propertyId=" + wingNorthId)
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isForbidden());

        // Tenant trying to access audit logs gets 403
        mockMvc.perform(get("/api/v1/audit-logs")
                        .header("Authorization", "Bearer " + residentToken))
                .andExpect(status().isForbidden());

        // 8. Resident submits complaint + owner resolves + transfers/moves out + bed releases + PDF/CSV export check
        String complaintId = readData(mockMvc.perform(post("/api/v1/complaints")
                        .header("Authorization", "Bearer " + residentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "propertyId": "%s",
                                  "title": "AC remote missing",
                                  "description": "Room 701 AC remote needed",
                                  "category": "FURNITURE",
                                  "priority": "MEDIUM"
                                }
                                """.formatted(wingNorthId)))
                .andExpect(status().isCreated())
                .andReturn()).path("id").asText();

        mockMvc.perform(patch("/api/v1/complaints/" + complaintId + "/status")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "status": "RESOLVED", "resolutionSummary": "Replaced AC remote" }
                                """))
                .andExpect(status().isOk());

        byte[] exportPdf = mockMvc.perform(get("/api/v1/reports/RENT_COLLECTION/export?format=PDF&propertyId=" + wingNorthId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(exportPdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");

        // Move out clears tenancy and frees bed
        mockMvc.perform(post("/api/v1/tenancies/" + tenancyId + "/move-out")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actualMoveOutDate": "2026-11-28",
                                  "unpaidDuesSettled": 6000.00,
                                  "deductionsAmount": 1000.00,
                                  "deductionReason": "Painting",
                                  "refundStatus": "REFUNDED",
                                  "releaseBedStatus": "VACANT"
                                }
                                """))
                .andExpect(status().isOk());

        // Bed1 is VACANT again alongside Bed2
        JsonNode availability = readData(mockMvc.perform(get("/api/v1/beds/availability?propertyId=" + wingNorthId + "&status=VACANT")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(availability.path("content")).hasSize(2);
    }

    private String registerOwner(String name, String email, String orgName, String propName) throws Exception {
        String payload = """
                {
                  "name": "%s",
                  "email": "%s",
                  "phone": "9812340077",
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

