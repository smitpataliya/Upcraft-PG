package com.upcraft.stay.electricity;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class Milestone4ElectricityIntegrationTest {

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
    void metersPhotoUploadTariffSnapshotSharedSplitAndIsolation_workEndToEnd() throws Exception {
        String registerPayload = """
                {
                  "name": "Electricity Owner",
                  "email": "elec.owner.m4@upcraft.test",
                  "phone": "9812340001",
                  "password": "Password@123",
                  "organizationName": "PowerGrid PG Group",
                  "propertyName": "PowerGrid Wing",
                  "propertyType": "PG",
                  "addressLine": "45 Park View",
                  "city": "Pune",
                  "state": "Maharashtra",
                  "pinCode": "411001"
                }
                """;
        String ownerToken = readToken(mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerPayload))
                .andExpect(status().isCreated())
                .andReturn());

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
                                  "number": "401",
                                  "roomType": "DOUBLE",
                                  "sharing": "2-Sharing",
                                  "capacity": 2,
                                  "baseMonthlyRent": 9500.00,
                                  "defaultDeposit": 12000.00,
                                  "amenities": ["AC"],
                                  "autoCreateBeds": true
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn());
        String roomId = roomData.path("id").asText();
        String bed1Id = roomData.path("beds").get(0).path("id").asText();
        String bed2Id = roomData.path("beds").get(1).path("id").asText();

        // Onboard Resident 1 & Resident 2 into Room 401
        mockMvc.perform(post("/api/v1/tenancies/move-in")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bedId": "%s",
                                  "fullName": "Aarav Mehta",
                                  "phone": "9812340011",
                                  "email": "aarav.m4@upcraft.test",
                                  "moveInDate": "2026-11-01",
                                  "agreedMonthlyRent": 9500.00,
                                  "securityDepositAmount": 12000.00,
                                  "sendPortalInvite": false
                                }
                                """.formatted(bed1Id)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/tenancies/move-in")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bedId": "%s",
                                  "fullName": "Kabir Verma",
                                  "phone": "9812340012",
                                  "email": "kabir.m4@upcraft.test",
                                  "moveInDate": "2026-11-01",
                                  "agreedMonthlyRent": 9500.00,
                                  "securityDepositAmount": 12000.00,
                                  "sendPortalInvite": false
                                }
                                """.formatted(bed2Id)))
                .andExpect(status().isCreated());

        String memberCode = readData(mockMvc.perform(post("/api/v1/invitations")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "aarav.m4@upcraft.test",
                                  "phone": "9812340011",
                                  "inviteeName": "Aarav Mehta",
                                  "targetRole": "PG_MEMBER",
                                  "propertyId": "%s"
                                }
                                """.formatted(propertyId)))
                .andExpect(status().isCreated())
                .andReturn()).path("inviteCode").asText();
        String memberToken = readToken(mockMvc.perform(post("/api/v1/invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "code": "%s", "name": "Aarav Mehta", "phone": "9812340011", "password": "Password@123" }
                                """.formatted(memberCode)))
                .andExpect(status().isOk())
                .andReturn());

        // Separate organization owner/tenant cannot download or touch PowerGrid files/meters
        String otherToken = readToken(mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Other Org",
                                  "email": "other.m4@upcraft.test",
                                  "phone": "9812349999",
                                  "password": "Password@123",
                                  "organizationName": "Other Group",
                                  "propertyName": "Other Tower",
                                  "propertyType": "PG",
                                  "addressLine": "9 Cross",
                                  "city": "Bengaluru",
                                  "state": "Karnataka",
                                  "pinCode": "560001"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn());

        // Invite Manager WITH canEnterMeters=true and canRecordPayments=false
        String mgrCode = readData(mockMvc.perform(post("/api/v1/invitations")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "meter.mgr@upcraft.test",
                                  "phone": "9812340022",
                                  "inviteeName": "Meter Warden",
                                  "targetRole": "MANAGER",
                                  "propertyId": "%s",
                                  "canManageTenants": true,
                                  "canRecordPayments": false,
                                  "canEnterMeters": true,
                                  "canHandleComplaints": true,
                                  "canPublishNotices": true
                                }
                                """.formatted(propertyId)))
                .andExpect(status().isCreated())
                .andReturn()).path("inviteCode").asText();
        String mgrToken = readToken(mockMvc.perform(post("/api/v1/invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "code": "%s", "name": "Meter Warden", "phone": "9812340022", "password": "Password@123" }
                                """.formatted(mgrCode)))
                .andExpect(status().isOk())
                .andReturn());

        // Register Room 401 Meter (rate=10, fixed=100, initial=100, EQUAL split)
        String meterId = readData(mockMvc.perform(post("/api/v1/meters")
                        .header("Authorization", "Bearer " + mgrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "propertyId": "%s",
                                  "roomId": "%s",
                                  "meterNumber": "MTR-401",
                                  "meterLabel": "Room 401 Sub-Meter",
                                  "meterScope": "ROOM",
                                  "unitRate": 10.00,
                                  "fixedCharge": 100.00,
                                  "splitMethod": "EQUAL",
                                  "initialReading": 100.00
                                }
                                """.formatted(propertyId, roomId)))
                .andExpect(status().isCreated())
                .andReturn()).path("id").asText();

        // Private meter photo upload
        MockMultipartFile photoPart = new MockMultipartFile(
                "file", "meter-nov.png", "image/png", "PNG-BINARY-PROOF".getBytes(StandardCharsets.UTF_8)
        );
        String photoFileId = readData(mockMvc.perform(multipart("/api/v1/files/upload")
                        .file(photoPart)
                        .param("propertyId", propertyId)
                        .param("category", "METER_PHOTO")
                        .header("Authorization", "Bearer " + mgrToken))
                .andExpect(status().isCreated())
                .andReturn()).path("id").asText();

        // Manager records November reading: 100 -> 180 (80 units * 10 + 100 fixed = 900.00)
        JsonNode novReading = readData(mockMvc.perform(post("/api/v1/meters/" + meterId + "/readings")
                        .header("Authorization", "Bearer " + mgrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "billingYear": 2026,
                                  "billingMonth": 11,
                                  "readingDate": "2026-11-30",
                                  "currentValue": 180.00,
                                  "photoFileId": "%s",
                                  "notes": "Month end AC + heater reading"
                                }
                                """.formatted(photoFileId)))
                .andExpect(status().isCreated())
                .andReturn());
        String novReadingId = novReading.path("id").asText();
        assertThat(novReading.path("unitsConsumed").asDouble()).isEqualTo(80.0);
        assertThat(novReading.path("totalAmount").asDouble()).isEqualTo(900.0);

        // Duplicate reading for same meter and 2026-11 rejected (409)
        mockMvc.perform(post("/api/v1/meters/" + meterId + "/readings")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "billingYear": 2026,
                                  "billingMonth": 11,
                                  "readingDate": "2026-11-30",
                                  "currentValue": 190.00
                                }
                                """))
                .andExpect(status().isConflict());

        // Manager without canRecordPayments cannot approve reading (403); Owner approves & splits
        mockMvc.perform(post("/api/v1/meter-readings/" + novReadingId + "/approve")
                        .header("Authorization", "Bearer " + mgrToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/meter-readings/" + novReadingId + "/approve")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());

        JsonNode splitBills = readData(mockMvc.perform(post("/api/v1/meter-readings/" + novReadingId + "/generate-bills")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isCreated())
                .andReturn());
        assertThat(splitBills).hasSize(2);
        assertThat(splitBills.get(0).path("totalAmount").asDouble()).isEqualTo(450.0);
        assertThat(splitBills.get(1).path("totalAmount").asDouble()).isEqualTo(450.0);
        assertThat(splitBills.get(0).path("invoiceId").asText()).isNotBlank();

        // Resident Aarav sees own split bill + downloads authorized meter photo
        JsonNode myBills = readData(mockMvc.perform(get("/api/v1/electricity/bills/mine")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andReturn()).path("content");
        assertThat(myBills).hasSize(1);
        assertThat(myBills.get(0).path("sharePercentage").asDouble()).isEqualTo(50.0);
        assertThat(myBills.get(0).path("totalAmount").asDouble()).isEqualTo(450.0);

        mockMvc.perform(get("/api/v1/files/" + photoFileId + "/content")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk());

        // Cross-org private file access blocked (403)
        mockMvc.perform(get("/api/v1/files/" + photoFileId + "/content")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());

        // Linked ELECTRICITY invoice shows up in Aarav's /api/v1/invoices/mine
        JsonNode myInvoices = readData(mockMvc.perform(get("/api/v1/invoices/mine")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andReturn()).path("content");
        assertThat(myInvoices).hasSize(1);
        assertThat(myInvoices.get(0).path("invoiceType").asText()).isEqualTo("ELECTRICITY");
        assertThat(myInvoices.get(0).path("totalAmount").asDouble()).isEqualTo(450.0);

        // Update meter tariff rate to 15.00 -> November historical bill remains 10.00/unit & 450.00 total
        mockMvc.perform(put("/api/v1/meters/" + meterId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "meterLabel": "Room 401 Revised Tariff",
                                  "unitRate": 15.00,
                                  "fixedCharge": 200.00,
                                  "splitMethod": "EQUAL",
                                  "meterStatus": "ACTIVE"
                                }
                                """))
                .andExpect(status().isOk());

        JsonNode historicalCheck = readData(mockMvc.perform(get("/api/v1/electricity/bills?meterId=" + meterId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn()).path("content");
        assertThat(historicalCheck.get(0).path("ratePerUnitSnapshot").asDouble()).isEqualTo(10.0);
        assertThat(historicalCheck.get(0).path("totalAmount").asDouble()).isEqualTo(450.0);

        // Lower reading without reset permission rejected (400)
        mockMvc.perform(post("/api/v1/meters/" + meterId + "/readings")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "billingYear": 2026,
                                  "billingMonth": 12,
                                  "readingDate": "2026-12-31",
                                  "currentValue": 50.00
                                }
                                """))
                .andExpect(status().isBadRequest());

        // High December jump (180 -> 450 = 270 units > 2.5 * 80) flags unusualUsage=true automatically
        JsonNode decSpike = readData(mockMvc.perform(post("/api/v1/meters/" + meterId + "/readings")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "billingYear": 2026,
                                  "billingMonth": 12,
                                  "readingDate": "2026-12-31",
                                  "currentValue": 450.00,
                                  "autoApprove": true
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn());
        assertThat(decSpike.path("unusualUsage").asBoolean()).isTrue();
        assertThat(decSpike.path("ratePerUnitSnapshot").asDouble()).isEqualTo(15.0);

        // Authorized meter reset passes (2027-01, current=30 < previous=450)
        JsonNode janReset = readData(mockMvc.perform(post("/api/v1/meters/" + meterId + "/readings")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "billingYear": 2027,
                                  "billingMonth": 1,
                                  "readingDate": "2027-01-31",
                                  "currentValue": 30.00,
                                  "allowResetOrCorrection": true,
                                  "correctionReason": "Replaced faulty meter hardware with fresh unit",
                                  "autoApprove": true
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn());
        assertThat(janReset.path("meterResetApplied").asBoolean()).isTrue();
        assertThat(janReset.path("unitsConsumed").asDouble()).isEqualTo(30.0);

        // Consumption report reconciles 80 + 270 + 30 = 380 units across 3 readings
        JsonNode report = readData(mockMvc.perform(get("/api/v1/electricity/consumption-report?propertyId=" + propertyId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(report.path("readingsSubmitted").asLong()).isEqualTo(3L);
        assertThat(report.path("unusualReadingsCount").asLong()).isEqualTo(1L);
        assertThat(report.path("totalUnitsConsumed").asDouble()).isEqualTo(380.0);
    }

    private String readToken(MvcResult result) throws Exception {
        return readData(result).path("token").asText();
    }

    private JsonNode readData(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }
}
