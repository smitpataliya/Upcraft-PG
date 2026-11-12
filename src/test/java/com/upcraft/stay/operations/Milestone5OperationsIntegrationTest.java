package com.upcraft.stay.operations;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class Milestone5OperationsIntegrationTest {

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
    void complaintsCommentsPhotosAnnouncementsMenuDocumentsTasksAndNotifications_workEndToEnd() throws Exception {
        String ownerToken = registerOwner("Ops Owner", "ops.owner.m5@upcraft.test", "Ops PG Group", "Ops Wing A");
        String propertyId = readData(mockMvc.perform(get("/api/v1/properties")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn()).path("content").get(0).path("id").asText();

        String otherOwnerToken = registerOwner("Other Owner", "other.owner.m5@upcraft.test", "Other Group", "Other Wing");

        // Create room + bed + move-in tenant + invite resident
        JsonNode roomData = readData(mockMvc.perform(post("/api/v1/properties/" + propertyId + "/rooms")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "floorNumber": 1,
                                  "number": "501",
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

        String bedId = roomData.path("beds").get(0).path("id").asText();

        String tenantId = readData(mockMvc.perform(post("/api/v1/tenancies/move-in")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bedId": "%s",
                                  "fullName": "Resident Ops",
                                  "phone": "9876500501",
                                  "email": "resident.ops.m5@upcraft.test",
                                  "moveInDate": "2026-11-01",
                                  "agreedMonthlyRent": 9500.00,
                                  "securityDepositAmount": 15000.00,
                                  "depositReceivedAmount": 15000.00,
                                  "rentDueDay": 5,
                                  "sendPortalInvite": false
                                }
                                """.formatted(bedId)))
                .andExpect(status().isCreated())
                .andReturn()).path("tenantId").asText();

        String inviteCode = readData(mockMvc.perform(post("/api/v1/invitations")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "propertyId": "%s",
                                  "email": "resident.ops.m5@upcraft.test",
                                  "phone": "9876500501",
                                  "inviteeName": "Resident Ops",
                                  "targetRole": "PG_MEMBER"
                                }
                                """.formatted(propertyId)))
                .andExpect(status().isCreated())
                .andReturn()).path("inviteCode").asText();

        String memberToken = readToken(mockMvc.perform(post("/api/v1/invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "%s",
                                  "name": "Resident Ops",
                                  "phone": "9876500501",
                                  "password": "Password@123"
                                }
                                """.formatted(inviteCode)))
                .andExpect(status().isOk())
                .andReturn());

        // 1. Resident uploads complaint photo & submits complaint
        MockMultipartFile complaintPhoto = new MockMultipartFile(
                "file", "leak.png", "image/png", "fake-png".getBytes(StandardCharsets.UTF_8)
        );
        String photoFileId = readData(mockMvc.perform(multipart("/api/v1/files/upload")
                        .file(complaintPhoto)
                        .param("propertyId", propertyId)
                        .param("category", "COMPLAINT_PHOTO")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isCreated())
                .andReturn()).path("id").asText();

        String complaintId = readData(mockMvc.perform(post("/api/v1/complaints")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "propertyId": "%s",
                                  "title": "Bathroom tap leaking",
                                  "description": "Constant drip in Room 501",
                                  "category": "PLUMBING",
                                  "priority": "HIGH",
                                  "preferredContactTime": "6 PM - 8 PM",
                                  "attachmentFileIds": ["%s"]
                                }
                                """.formatted(propertyId, photoFileId)))
                .andExpect(status().isCreated())
                .andReturn()).path("id").asText();

        // Owner posts internal note + public reply, resolves complaint
        mockMvc.perform(post("/api/v1/complaints/" + complaintId + "/comments")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "Internal vendor cost note: 300 INR",
                                  "internal": true
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/complaints/" + complaintId + "/comments")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "Plumber scheduled today at 6 PM",
                                  "internal": false
                                }
                                """))
                .andExpect(status().isCreated());

        // Resident detail excludes internal note
        JsonNode memberComplaintDetail = readData(mockMvc.perform(get("/api/v1/complaints/" + complaintId)
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(memberComplaintDetail.path("comments").size()).isEqualTo(1);
        assertThat(memberComplaintDetail.path("attachments").size()).isEqualTo(1);

        // Resident cannot post internal note
        mockMvc.perform(post("/api/v1/complaints/" + complaintId + "/comments")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "Trying internal",
                                  "internal": true
                                }
                                """))
                .andExpect(status().isForbidden());

        // Owner resolves, resident reopens
        mockMvc.perform(patch("/api/v1/complaints/" + complaintId + "/status")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "RESOLVED",
                                  "resolutionSummary": "Replaced washer"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/complaints/" + complaintId + "/status")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "REOPENED",
                                  "note": "Still dripping slightly"
                                }
                                """))
                .andExpect(status().isOk());

        // Cross-org owner rejected
        mockMvc.perform(get("/api/v1/complaints/" + complaintId)
                        .header("Authorization", "Bearer " + otherOwnerToken))
                .andExpect(status().isForbidden());

        // 2. Announcement publish + idempotent read tracking
        String announcementId = readData(mockMvc.perform(post("/api/v1/announcements")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "propertyId": "%s",
                                  "title": "Water tank cleaning",
                                  "body": "Supply off 10 AM to 12 PM",
                                  "category": "MAINTENANCE",
                                  "audienceType": "SELECTED_PROPERTY",
                                  "pinned": true
                                }
                                """.formatted(propertyId)))
                .andExpect(status().isCreated())
                .andReturn()).path("id").asText();

        JsonNode firstRead = readData(mockMvc.perform(post("/api/v1/announcements/" + announcementId + "/read")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(firstRead.path("alreadyRead").asBoolean()).isFalse();

        JsonNode secondRead = readData(mockMvc.perform(post("/api/v1/announcements/" + announcementId + "/read")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(secondRead.path("alreadyRead").asBoolean()).isTrue();

        // 3. Property-scoped Food Menu
        mockMvc.perform(post("/api/v1/food-menu")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "propertyId": "%s",
                                  "name": "Poha & Chai",
                                  "type": "BREAKFAST",
                                  "servingTime": "08:00 AM",
                                  "veg": true,
                                  "available": true,
                                  "published": true,
                                  "dateScheduled": "2026-11-12"
                                }
                                """.formatted(propertyId)))
                .andExpect(status().isCreated());

        JsonNode menuList = readData(mockMvc.perform(get("/api/v1/food-menu/weekly")
                        .param("startDate", "2026-11-12")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(menuList.size()).isGreaterThanOrEqualTo(1);

        // 4. Private Documents: hidden vs shared enforcement
        MockMultipartFile docPdf = new MockMultipartFile(
                "file", "agreement.pdf", "application/pdf", "pdf-bytes".getBytes(StandardCharsets.UTF_8)
        );
        String hiddenFileId = readData(mockMvc.perform(multipart("/api/v1/files/upload")
                        .file(docPdf)
                        .param("propertyId", propertyId)
                        .param("category", "TENANT_DOCUMENT")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isCreated())
                .andReturn()).path("id").asText();

        mockMvc.perform(post("/api/v1/documents")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "propertyId": "%s",
                                  "storedFileId": "%s",
                                  "scopeType": "TENANT",
                                  "documentCategory": "AGREEMENT",
                                  "title": "Internal Owner Copy",
                                  "tenantId": "%s",
                                  "sharedWithTenant": false
                                }
                                """.formatted(propertyId, hiddenFileId, tenantId)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/files/" + hiddenFileId + "/content")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isForbidden());

        // 5. Manager Task + Push Token + Notification Preferences + Unread Count
        String taskId = readData(mockMvc.perform(post("/api/v1/manager-tasks")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "propertyId": "%s",
                                  "complaintId": "%s",
                                  "title": "Inspect Room 501 washer",
                                  "priority": "HIGH"
                                }
                                """.formatted(propertyId, complaintId)))
                .andExpect(status().isCreated())
                .andReturn()).path("id").asText();

        mockMvc.perform(patch("/api/v1/manager-tasks/" + taskId + "/status")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "taskStatus": "COMPLETED",
                                  "completionNotes": "Verified fixed"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/push-tokens")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": "ExponentPushToken[test-token-m5]",
                                  "platform": "ANDROID",
                                  "deviceName": "Pixel 8"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/notifications/preferences")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "inAppEnabled": true,
                                  "pushEnabled": true,
                                  "emailEnabled": false,
                                  "smsEnabled": false,
                                  "whatsappEnabled": false
                                }
                                """))
                .andExpect(status().isOk());

        JsonNode unreadData = readData(mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(unreadData.path("unreadCount").asLong()).isGreaterThanOrEqualTo(1L);
    }

    private String registerOwner(String name, String email, String orgName, String propName) throws Exception {
        String payload = """
                {
                  "name": "%s",
                  "email": "%s",
                  "phone": "9812340099",
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
        return readToken(mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn());
    }

    private String readToken(MvcResult result) throws Exception {
        return readData(result).path("token").asText();
    }

    private JsonNode readData(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }
}
