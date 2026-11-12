package com.upcraft.stay.auth;

import java.util.UUID;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class Milestone1AuthAndSecurityIntegrationTest {

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
  void ownerOnboardingManagerScopingAndLegacyLogin_enforcesStrictPropertyIsolation() throws Exception {
    String ownerPayload = """
        {
          "name": "Aarav Shah",
          "email": "aarav.owner@upcraft.test",
          "phone": "9876543210",
          "password": "Password@123",
          "organizationName": "Aarav Stays",
          "propertyName": "Aarav Residency One",
          "propertyType": "PG",
          "addressLine": "45 Navrangpura",
          "city": "Ahmedabad",
          "state": "Gujarat",
          "pinCode": "380009"
        }
        """;

    MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/register")
        .contentType(MediaType.APPLICATION_JSON)
        .content(ownerPayload))
        .andExpect(status().isCreated())
        .andExpect(header().exists("X-Trace-Id"))
        .andReturn();

    JsonNode registerJson = objectMapper.readTree(registerResult.getResponse().getContentAsString());
    String ownerToken = registerJson.path("data").path("token").asText();
    assertThat(ownerToken).isNotBlank();
    assertThat(registerJson.path("data").path("user").path("role").asText()).isEqualTo("PG_OWNER");

    // Verify legacy /api/auth/login remains backward-compatible and returns nested
    // user + flat fields
    String legacyLoginBody = """
        { "email": "aarav.owner@upcraft.test", "password": "Password@123" }
        """;
    MvcResult legacyResult = mockMvc.perform(post("/api/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content(legacyLoginBody))
        .andExpect(status().isOk())
        .andReturn();

    JsonNode legacyJson = objectMapper.readTree(legacyResult.getResponse().getContentAsString());
    assertThat(legacyJson.path("token").asText()).isNotBlank();
    assertThat(legacyJson.path("role").asText()).isEqualTo("PG_OWNER");
    assertThat(legacyJson.path("user").path("organizationName").asText()).isEqualTo("Aarav Stays");

    // Owner creates a second property
    String secondPropertyBody = """
        {
          "name": "Aarav Residency Two",
          "propertyType": "HOSTEL",
          "addressLine": "88 SG Highway",
          "city": "Ahmedabad",
          "state": "Gujarat",
          "pinCode": "380054"
        }
        """;
    MvcResult secondPropertyResult = mockMvc.perform(post("/api/v1/properties")
        .header("Authorization", "Bearer " + ownerToken)
        .contentType(MediaType.APPLICATION_JSON)
        .content(secondPropertyBody))
        .andExpect(status().isCreated())
        .andReturn();
    UUID propertyTwoId = UUID.fromString(
        objectMapper.readTree(secondPropertyResult.getResponse().getContentAsString())
            .path("data").path("id").asText());

    // Fetch owner properties -> locate first property ID
    MvcResult listPropsResult = mockMvc.perform(get("/api/v1/properties")
        .header("Authorization", "Bearer " + ownerToken))
        .andExpect(status().isOk())
        .andReturn();
    JsonNode propsContent = objectMapper.readTree(listPropsResult.getResponse().getContentAsString())
        .path("data").path("content");
    assertThat(propsContent).hasSize(2);
    UUID propertyOneId = propsContent.get(0).path("id").asText().equals(propertyTwoId.toString())
        ? UUID.fromString(propsContent.get(1).path("id").asText())
        : UUID.fromString(propsContent.get(0).path("id").asText());

    // Owner invites manager ONLY for Property One
    String invitePayload = """
        {
          "email": "warden.one@upcraft.test",
          "phone": "9876543219",
          "inviteeName": "Kiran Warden",
          "targetRole": "MANAGER",
          "propertyId": "%s",
          "canManageTenants": true,
          "canRecordPayments": true,
          "canEnterMeters": true,
          "canHandleComplaints": true,
          "canPublishNotices": false
        }
        """.formatted(propertyOneId);

    MvcResult inviteResult = mockMvc.perform(post("/api/v1/invitations")
        .header("Authorization", "Bearer " + ownerToken)
        .contentType(MediaType.APPLICATION_JSON)
        .content(invitePayload))
        .andExpect(status().isCreated())
        .andReturn();
    String inviteCode = objectMapper.readTree(inviteResult.getResponse().getContentAsString())
        .path("data").path("inviteCode").asText();
    assertThat(inviteCode).hasSize(8);

    // Manager verifies and accepts invitation
    mockMvc.perform(get("/api/v1/invitations/verify").param("code", inviteCode))
        .andExpect(status().isOk());

    String acceptPayload = """
        {
          "code": "%s",
          "name": "Kiran Warden",
          "phone": "9876543219",
          "password": "ManagerPass@123"
        }
        """.formatted(inviteCode);

    MvcResult acceptResult = mockMvc.perform(post("/api/v1/invitations/accept")
        .contentType(MediaType.APPLICATION_JSON)
        .content(acceptPayload))
        .andExpect(status().isOk())
        .andReturn();
    String managerToken = objectMapper.readTree(acceptResult.getResponse().getContentAsString())
        .path("data").path("token").asText();

    // Manager sees ONLY Property One
    MvcResult mgrPropsResult = mockMvc.perform(get("/api/v1/properties")
        .header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk())
        .andReturn();
    JsonNode mgrContent = objectMapper.readTree(mgrPropsResult.getResponse().getContentAsString())
        .path("data").path("content");
    assertThat(mgrContent).hasSize(1);
    assertThat(mgrContent.get(0).path("id").asText()).isEqualTo(propertyOneId.toString());

    // Manager cannot access Property Two -> 403
    mockMvc.perform(get("/api/v1/properties/{id}", propertyTwoId)
        .header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isForbidden());

    // Manager cannot create properties -> 403
    mockMvc.perform(post("/api/v1/properties")
        .header("Authorization", "Bearer " + managerToken)
        .contentType(MediaType.APPLICATION_JSON)
        .content(secondPropertyBody))
        .andExpect(status().isForbidden());

    // Separate Owner B cannot access Owner A's Property One -> 403
    String otherOwnerPayload = """
        {
          "name": "Vikram Mehta",
          "email": "vikram.owner@upcraft.test",
          "phone": "9876500001",
          "password": "Password@123",
          "organizationName": "Vikram Stays",
          "propertyName": "Vikram PG",
          "propertyType": "PG",
          "addressLine": "10 MG Road",
          "city": "Pune",
          "state": "Maharashtra",
          "pinCode": "411001"
        }
        """;
    MvcResult otherReg = mockMvc.perform(post("/api/v1/auth/register")
        .contentType(MediaType.APPLICATION_JSON)
        .content(otherOwnerPayload))
        .andExpect(status().isCreated())
        .andReturn();
    String otherOwnerToken = objectMapper.readTree(otherReg.getResponse().getContentAsString())
        .path("data").path("token").asText();

    mockMvc.perform(get("/api/v1/properties/{id}", propertyOneId)
        .header("Authorization", "Bearer " + otherOwnerToken))
        .andExpect(status().isForbidden());
  }
}
