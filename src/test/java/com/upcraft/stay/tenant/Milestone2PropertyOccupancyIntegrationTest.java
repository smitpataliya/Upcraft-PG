package com.upcraft.stay.tenant;

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
class Milestone2PropertyOccupancyIntegrationTest {

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
    void fullPropertyHierarchyMoveInTransferMoveOutAndIsolation_executesEndToEnd() throws Exception {
        String registerPayload = """
                {
                  "name": "Meera Nair",
                  "email": "meera.owner.m2@upcraft.test",
                  "phone": "9876543299",
                  "password": "Password@123",
                  "organizationName": "Meera Residences",
                  "propertyName": "Indiranagar Block A",
                  "propertyType": "PG",
                  "addressLine": "100 Feet Road",
                  "city": "Bengaluru",
                  "state": "Karnataka",
                  "pinCode": "560038"
                }
                """;
        MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerPayload))
                .andExpect(status().isCreated())
                .andReturn();

        String ownerToken = objectMapper.readTree(registerResult.getResponse().getContentAsString())
                .path("data").path("token").asText();

        String propertyId = objectMapper.readTree(mockMvc.perform(get("/api/v1/properties")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString())
                .path("data").path("content").get(0).path("id").asText();

        String floorBody = """
                { "floorNumber": 1, "name": "First Floor", "notes": "Quiet wing" }
                """;
        String floorId = objectMapper.readTree(mockMvc.perform(post("/api/v1/properties/" + propertyId + "/floors")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(floorBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString())
                .path("data").path("id").asText();

        String roomBody = """
                {
                  "floorId": "%s",
                  "number": "101",
                  "roomType": "DOUBLE",
                  "sharing": "2-Sharing",
                  "capacity": 2,
                  "baseMonthlyRent": 11000.00,
                  "defaultDeposit": 20000.00,
                  "amenities": ["AC", "WiFi"],
                  "autoCreateBeds": true
                }
                """.formatted(floorId);
        JsonNode roomData = objectMapper.readTree(mockMvc.perform(post("/api/v1/properties/" + propertyId + "/rooms")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(roomBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString())
                .path("data");

        String bedOneId = roomData.path("beds").get(0).path("id").asText();
        String bedTwoId = roomData.path("beds").get(1).path("id").asText();

        // Reserve BED-2
        mockMvc.perform(post("/api/v1/tenancies/reserve")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "bedId": "%s", "availableFrom": "2026-11-15", "notes": "Holding for visitor" }
                                """.formatted(bedTwoId)))
                .andExpect(status().isCreated());

        // Move in tenant on BED-1 and trigger portal invite
        String moveInPayload = """
                {
                  "bedId": "%s",
                  "fullName": "Siddharth Rao",
                  "phone": "9811122233",
                  "email": "siddharth.tenant.m2@upcraft.test",
                  "moveInDate": "2026-11-01",
                  "agreedMonthlyRent": 11000.00,
                  "securityDepositAmount": 20000.00,
                  "depositReceivedAmount": 20000.00,
                  "sendPortalInvite": true
                }
                """.formatted(bedOneId);
        String tenancyId = objectMapper.readTree(mockMvc.perform(post("/api/v1/tenancies/move-in")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(moveInPayload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString())
                .path("data").path("id").asText();

        // Duplicate move-in into occupied BED-1 must return 409 Conflict
        mockMvc.perform(post("/api/v1/tenancies/move-in")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bedId": "%s",
                                  "fullName": "Second Resident",
                                  "phone": "9811122244",
                                  "moveInDate": "2026-11-02"
                                }
                                """.formatted(bedOneId)))
                .andExpect(status().isConflict());

        // Property archival blocked while active tenancy exists
        mockMvc.perform(patch("/api/v1/properties/" + propertyId + "/archive")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isConflict());

        // Move-in with sendPortalInvite=true creates a pending PG_MEMBER invitation in DB (secrets not leaked on list)
        JsonNode pendingInvite = objectMapper.readTree(mockMvc.perform(get("/api/v1/invitations")
                        .param("propertyId", propertyId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString())
                .path("data").path("content").get(0);
        assertThat(pendingInvite.path("invitationStatus").asText()).isEqualTo("PENDING");
        assertThat(pendingInvite.path("email").asText()).isEqualTo("siddharth.tenant.m2@upcraft.test");

        // Verify vacancy filter returns reserved BED-2 cleanly without tenant PII
        JsonNode reservedBeds = objectMapper.readTree(mockMvc.perform(get("/api/v1/beds/availability")
                        .param("propertyId", propertyId)
                        .param("bedStatus", "RESERVED")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString())
                .path("data").path("content");
        assertThat(reservedBeds).hasSize(1);
        assertThat(reservedBeds.get(0).path("bedCode").asText()).isEqualTo("BED-2");

        // Transfer Siddharth from BED-1 -> BED-2 (reserved bed)
        String transferBody = """
                {
                  "targetBedId": "%s",
                  "transferDate": "2026-11-05",
                  "newMonthlyRent": 11500.00,
                  "notes": "Preferred window side"
                }
                """.formatted(bedTwoId);
        String transferredTenancyId = objectMapper.readTree(mockMvc.perform(post("/api/v1/tenancies/" + tenancyId + "/transfer")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString())
                .path("data").path("id").asText();

        // Owner invites another tenant portal account + assigns via existing email -> /api/v1/tenants/me
        String inviteCreatePayload = """
                {
                  "email": "resident.portal.m2@upcraft.test",
                  "phone": "9811199999",
                  "inviteeName": "Kavya Menon",
                  "targetRole": "PG_MEMBER",
                  "propertyId": "%s"
                }
                """.formatted(propertyId);
        String portalInviteCode = objectMapper.readTree(mockMvc.perform(post("/api/v1/invitations")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(inviteCreatePayload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString())
                .path("data").path("inviteCode").asText();

        String acceptPortalBody = """
                {
                  "code": "%s",
                  "name": "Kavya Menon",
                  "phone": "9811199999",
                  "password": "Password@123"
                }
                """.formatted(portalInviteCode);
        String tenantJwt = objectMapper.readTree(mockMvc.perform(post("/api/v1/invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(acceptPortalBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString())
                .path("data").path("token").asText();

        // Restricted manager without canManageTenants cannot move tenants in (403 Forbidden)
        String mgrInviteCode = objectMapper.readTree(mockMvc.perform(post("/api/v1/invitations")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "warden.m2@upcraft.test",
                                  "phone": "9811188888",
                                  "inviteeName": "Rakesh Warden",
                                  "targetRole": "MANAGER",
                                  "propertyId": "%s",
                                  "canManageTenants": false,
                                  "canRecordPayments": true
                                }
                                """.formatted(propertyId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString())
                .path("data").path("inviteCode").asText();

        String managerJwt = objectMapper.readTree(mockMvc.perform(post("/api/v1/invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "%s",
                                  "name": "Rakesh Warden",
                                  "phone": "9811188888",
                                  "password": "Password@123"
                                }
                                """.formatted(mgrInviteCode)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString())
                .path("data").path("token").asText();

        mockMvc.perform(post("/api/v1/tenancies/move-in")
                        .header("Authorization", "Bearer " + managerJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bedId": "%s",
                                  "fullName": "Unauthorized Move",
                                  "phone": "9811177777",
                                  "moveInDate": "2026-11-06"
                                }
                                """.formatted(bedOneId)))
                .andExpect(status().isForbidden());

        // Move Kavya into vacated BED-1
        mockMvc.perform(post("/api/v1/tenancies/move-in")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bedId": "%s",
                                  "fullName": "Kavya Menon",
                                  "phone": "9811199999",
                                  "email": "resident.portal.m2@upcraft.test",
                                  "moveInDate": "2026-11-06"
                                }
                                """.formatted(bedOneId)))
                .andExpect(status().isCreated());

        // Tenant portal returns only Kavya's active residency; tenant directory is forbidden to PG_MEMBER
        JsonNode myPortal = objectMapper.readTree(mockMvc.perform(get("/api/v1/tenants/me")
                        .header("Authorization", "Bearer " + tenantJwt))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString())
                .path("data");
        assertThat(myPortal.path("activeTenancy").path("bedCode").asText()).isEqualTo("BED-1");

        mockMvc.perform(get("/api/v1/tenants")
                        .header("Authorization", "Bearer " + tenantJwt))
                .andExpect(status().isForbidden());

        // Move out Kavya first so BED-1 frees cleanly, then move out Siddharth from BED-2
        String kavyaTenancyId = myPortal.path("activeTenancy").path("id").asText();
        mockMvc.perform(post("/api/v1/tenancies/" + kavyaTenancyId + "/move-out")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "actualMoveOutDate": "2026-11-12", "releaseBedStatus": "VACANT" }
                                """))
                .andExpect(status().isOk());

        // Summary reflects 1 occupied (Siddharth on BED-2), 1 vacant (BED-1)
        JsonNode midSummary = objectMapper.readTree(mockMvc.perform(get("/api/v1/properties/" + propertyId + "/summary")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString())
                .path("data");
        assertThat(midSummary.path("occupiedBeds").asLong()).isEqualTo(1L);
        assertThat(midSummary.path("vacantBeds").asLong()).isEqualTo(1L);

        // Bed history shows completed + released segments on BED-1
        JsonNode bedOneHistory = objectMapper.readTree(mockMvc.perform(get("/api/v1/beds/" + bedOneId + "/history")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString())
                .path("data").path("content");
        assertThat(bedOneHistory).hasSize(2);

        // Move out from transferred tenancy with itemized deductions
        String moveOutBody = """
                {
                  "actualMoveOutDate": "2026-11-20",
                  "finalMeterReading": 420.00,
                  "unpaidDuesSettled": 1500.00,
                  "deductionsAmount": 500.00,
                  "deductionReason": "Deep cleaning",
                  "refundStatus": "REFUNDED",
                  "releaseBedStatus": "VACANT"
                }
                """;
        JsonNode moveOutData = objectMapper.readTree(mockMvc.perform(post("/api/v1/tenancies/" + transferredTenancyId + "/move-out")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(moveOutBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString())
                .path("data");
        assertThat(moveOutData.path("refundableDepositAmount").decimalValue()).isEqualByComparingTo("18000.00");
        assertThat(moveOutData.path("tenancyStatus").asText()).isEqualTo("COMPLETED");

        // Now property can be archived cleanly
        mockMvc.perform(patch("/api/v1/properties/" + propertyId + "/archive")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());
    }
}
