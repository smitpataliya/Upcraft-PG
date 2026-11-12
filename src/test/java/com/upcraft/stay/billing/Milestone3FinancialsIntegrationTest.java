package com.upcraft.stay.billing;

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

@SpringBootTest
@ActiveProfiles("test")
class Milestone3FinancialsIntegrationTest {

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
    void idempotentMonthlyInvoicesPartialPaymentsReversalDepositsAndIsolation_workEndToEnd() throws Exception {
        String registerPayload = """
                {
                  "name": "Vikram Desai",
                  "email": "vikram.owner.m3@upcraft.test",
                  "phone": "9876501122",
                  "password": "Password@123",
                  "organizationName": "Desai Living",
                  "propertyName": "Koramangala Prime PG",
                  "propertyType": "PG",
                  "addressLine": "4th Block",
                  "city": "Bengaluru",
                  "state": "Karnataka",
                  "pinCode": "560034"
                }
                """;
        String ownerToken = readToken(mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerPayload))
                .andExpect(status().isCreated())
                .andReturn());

        String propertyId = objectMapper.readTree(mockMvc.perform(get("/api/v1/properties")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString())
                .path("data").path("content").get(0).path("id").asText();

        String roomPayload = """
                {
                  "floorNumber": 1,
                  "number": "301",
                  "roomType": "DOUBLE",
                  "sharing": "2-Sharing",
                  "capacity": 2,
                  "baseMonthlyRent": 10000.00,
                  "defaultDeposit": 15000.00,
                  "amenities": ["AC", "WiFi"],
                  "autoCreateBeds": true
                }
                """;
        JsonNode roomData = readData(mockMvc.perform(post("/api/v1/properties/" + propertyId + "/rooms")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(roomPayload))
                .andExpect(status().isCreated())
                .andReturn());
        String bed1Id = roomData.path("beds").get(0).path("id").asText();
        String bed2Id = roomData.path("beds").get(1).path("id").asText();

        String moveInOnePayload = """
                {
                  "bedId": "%s",
                  "fullName": "Rohan Kapoor",
                  "phone": "9811112222",
                  "email": "rohan.m3@upcraft.test",
                  "moveInDate": "2026-11-01",
                  "agreedMonthlyRent": 10000.00,
                  "securityDepositAmount": 15000.00,
                  "depositReceivedAmount": 15000.00,
                  "rentDueDay": 5,
                  "sendPortalInvite": false
                }
                """.formatted(bed1Id);
        JsonNode tenancyOne = readData(mockMvc.perform(post("/api/v1/tenancies/move-in")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(moveInOnePayload))
                .andExpect(status().isCreated())
                .andReturn());
        String tenancyOneId = tenancyOne.path("id").asText();
        String tenantOneId = tenancyOne.path("tenantId").asText();

        String moveInTwoPayload = """
                {
                  "bedId": "%s",
                  "fullName": "Nikhil Shah",
                  "phone": "9811113333",
                  "email": "nikhil.m3@upcraft.test",
                  "moveInDate": "2026-11-01",
                  "agreedMonthlyRent": 8000.00,
                  "securityDepositAmount": 10000.00,
                  "depositReceivedAmount": 10000.00,
                  "rentDueDay": 5,
                  "sendPortalInvite": false
                }
                """.formatted(bed2Id);
        JsonNode tenancyTwo = readData(mockMvc.perform(post("/api/v1/tenancies/move-in")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(moveInTwoPayload))
                .andExpect(status().isCreated())
                .andReturn());
        String tenantTwoId = tenancyTwo.path("tenantId").asText();

        // Check initial move-in deposit recorded in deposit ledger
        JsonNode depositSummary = readData(mockMvc.perform(get("/api/v1/tenancies/" + tenancyOneId + "/deposits")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(depositSummary.path("collectedAmount").asDouble()).isEqualTo(15000.0);
        assertThat(depositSummary.path("heldBalance").asDouble()).isEqualTo(15000.0);

        // First idempotent generation creates 2 invoices
        String genPayload = """
                { "propertyId": "%s", "billingYear": 2026, "billingMonth": 11, "prorateMidMonth": false }
                """.formatted(propertyId);
        JsonNode genFirst = readData(mockMvc.perform(post("/api/v1/invoices/generate-rent")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(genPayload))
                .andExpect(status().isCreated())
                .andReturn());
        assertThat(genFirst.path("generatedCount").asInt()).isEqualTo(2);

        // Second generation for same period skips both cleanly (idempotent)
        JsonNode genSecond = readData(mockMvc.perform(post("/api/v1/invoices/generate-rent")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(genPayload))
                .andExpect(status().isCreated())
                .andReturn());
        assertThat(genSecond.path("generatedCount").asInt()).isEqualTo(0);
        assertThat(genSecond.path("skippedAlreadyBilledCount").asInt()).isEqualTo(2);

        // Resolve Tenant 1 and Tenant 2 invoice IDs
        String t1InvoiceId = readData(mockMvc.perform(get("/api/v1/invoices?tenantId=" + tenantOneId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn()).path("content").get(0).path("id").asText();
        String t2InvoiceId = readData(mockMvc.perform(get("/api/v1/invoices?tenantId=" + tenantTwoId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn()).path("content").get(0).path("id").asText();

        // Resident invite acceptance & isolation check
        String memberInvitePayload = """
                {
                  "email": "rohan.m3@upcraft.test",
                  "phone": "9811112222",
                  "inviteeName": "Rohan Kapoor",
                  "targetRole": "PG_MEMBER",
                  "propertyId": "%s"
                }
                """.formatted(propertyId);
        String inviteCode = readData(mockMvc.perform(post("/api/v1/invitations")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(memberInvitePayload))
                .andExpect(status().isCreated())
                .andReturn()).path("inviteCode").asText();
        String acceptMemberPayload = """
                { "code": "%s", "name": "Rohan Kapoor", "phone": "9811112222", "password": "Password@123" }
                """.formatted(inviteCode);
        String memberToken = readToken(mockMvc.perform(post("/api/v1/invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(acceptMemberPayload))
                .andExpect(status().isOk())
                .andReturn());

        JsonNode myInvoices = readData(mockMvc.perform(get("/api/v1/invoices/mine")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(myInvoices.path("content")).hasSize(1);
        assertThat(myInvoices.path("content").get(0).path("id").asText()).isEqualTo(t1InvoiceId);

        // Cross-tenant invoice access blocked (403)
        mockMvc.perform(get("/api/v1/invoices/" + t2InvoiceId)
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isForbidden());

        // Manager without canRecordPayments cannot record payments (403)
        String mgrInvitePayload = """
                {
                  "email": "no-pay-mgr@upcraft.test",
                  "phone": "9822223333",
                  "inviteeName": "ReadOnly Warden",
                  "targetRole": "MANAGER",
                  "propertyId": "%s",
                  "canManageTenants": true,
                  "canRecordPayments": false,
                  "canEnterMeters": true,
                  "canHandleComplaints": true,
                  "canPublishNotices": true
                }
                """.formatted(propertyId);
        String mgrCode = readData(mockMvc.perform(post("/api/v1/invitations")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mgrInvitePayload))
                .andExpect(status().isCreated())
                .andReturn()).path("inviteCode").asText();
        String mgrToken = readToken(mockMvc.perform(post("/api/v1/invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "code": "%s", "name": "ReadOnly Warden", "phone": "9822223333", "password": "Password@123" }
                                """.formatted(mgrCode)))
                .andExpect(status().isOk())
                .andReturn());

        String partialPayPayload = """
                {
                  "amount": 4000.00,
                  "paymentMethod": "UPI",
                  "referenceNumber": "UPI-M3-001",
                  "idempotencyKey": "pay-idem-1",
                  "notes": "Part 1 UPI"
                }
                """;
        mockMvc.perform(post("/api/v1/invoices/" + t1InvoiceId + "/payments")
                        .header("Authorization", "Bearer " + mgrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(partialPayPayload))
                .andExpect(status().isForbidden());

        // Unverified GATEWAY payment rejected (400)
        mockMvc.perform(post("/api/v1/invoices/" + t1InvoiceId + "/payments")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "amount": 4000.00, "paymentMethod": "GATEWAY" }
                                """))
                .andExpect(status().isBadRequest());

        // Owner records partial payment + idempotent retry
        JsonNode payment1 = readData(mockMvc.perform(post("/api/v1/invoices/" + t1InvoiceId + "/payments")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(partialPayPayload))
                .andExpect(status().isCreated())
                .andReturn());
        String payment1Id = payment1.path("id").asText();

        JsonNode payment1Retry = readData(mockMvc.perform(post("/api/v1/invoices/" + t1InvoiceId + "/payments")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(partialPayPayload))
                .andExpect(status().isCreated())
                .andReturn());
        assertThat(payment1Retry.path("id").asText()).isEqualTo(payment1Id);

        // Overpayment rejected (400)
        mockMvc.perform(post("/api/v1/invoices/" + t1InvoiceId + "/payments")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "amount": 7000.00, "paymentMethod": "CASH" }
                                """))
                .andExpect(status().isBadRequest());

        // Reverse partial payment -> invoice restores balance to 10000
        mockMvc.perform(post("/api/v1/payments/" + payment1Id + "/reverse")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "reason": "UPI settlement failed" }
                                """))
                .andExpect(status().isOk());

        JsonNode detailAfterReverse = readData(mockMvc.perform(get("/api/v1/invoices/" + t1InvoiceId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(detailAfterReverse.path("balanceDue").asDouble()).isEqualTo(10000.0);
        assertThat(detailAfterReverse.path("adjustments")).hasSize(1);

        // Pay full 10000 cleanly & inspect receipt
        String fullPayId = readData(mockMvc.perform(post("/api/v1/invoices/" + t1InvoiceId + "/payments")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "amount": 10000.00, "paymentMethod": "BANK_TRANSFER", "referenceNumber": "NEFT-8811" }
                                """))
                .andExpect(status().isCreated())
                .andReturn()).path("id").asText();

        JsonNode receipt = readData(mockMvc.perform(get("/api/v1/payments/" + fullPayId + "/receipt")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(receipt.path("amountPaid").asDouble()).isEqualTo(10000.0);
        assertThat(receipt.path("invoiceBalanceRemaining").asDouble()).isEqualTo(0.0);

        // Custom invoice created then waived -> excluded from summary totals
        String customInvId = readData(mockMvc.perform(post("/api/v1/invoices")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenancyId": "%s",
                                  "notes": "Key replacement fee",
                                  "items": [
                                    { "itemType": "ONE_TIME_CHARGE", "description": "Duplicate Key", "quantity": 1, "unitAmount": 500.00 }
                                  ]
                                }
                                """.formatted(tenancyOneId)))
                .andExpect(status().isCreated())
                .andReturn()).path("id").asText();

        mockMvc.perform(patch("/api/v1/invoices/" + customInvId + "/status")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "invoiceStatus": "WAIVED", "reason": "Owner goodwill waiver" }
                                """))
                .andExpect(status().isOk());

        // Billing summary separates rent revenue (18000 billed, 10000 collected, 8000 due) from 25000 deposit held
        JsonNode billingSummary = readData(mockMvc.perform(get("/api/v1/billing/summary?propertyId=" + propertyId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(billingSummary.path("totalBilled").asDouble()).isEqualTo(18000.0);
        assertThat(billingSummary.path("totalCollected").asDouble()).isEqualTo(10000.0);
        assertThat(billingSummary.path("totalOutstanding").asDouble()).isEqualTo(8000.0);
        assertThat(billingSummary.path("depositHeldTotal").asDouble()).isEqualTo(25000.0);

        // Deposit deduction + Tenant Ledger reconciliation
        mockMvc.perform(post("/api/v1/tenancies/" + tenancyOneId + "/deposits")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "transactionType": "DEDUCTION",
                                  "amount": 2000.00,
                                  "paymentMethod": "OTHER",
                                  "reason": "Painting touchup"
                                }
                                """))
                .andExpect(status().isCreated());

        JsonNode ledger = readData(mockMvc.perform(get("/api/v1/tenants/" + tenantOneId + "/ledger")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(ledger.path("totalBilled").asDouble()).isEqualTo(10000.0);
        assertThat(ledger.path("totalPaid").asDouble()).isEqualTo(10000.0);
        assertThat(ledger.path("depositHeld").asDouble()).isEqualTo(13000.0);
    }

    private String readToken(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("token").asText();
    }

    private JsonNode readData(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }
}

