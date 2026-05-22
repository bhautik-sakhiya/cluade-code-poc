package org.poc.claudecodepoc.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.poc.claudecodepoc.dto.request.SlotRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the full Spring Security filter chain.
 *
 * Flow under test:
 *   HTTP request → BearerTokenAuthenticationFilter extracts "Bearer <token>"
 *     → JwtDecoder.decode(token) validates and parses it
 *     → KeycloakJwtConverter maps realm_access.roles → ROLE_*
 *     → SecurityContext holds JwtAuthenticationToken
 *     → @PreAuthorize checks pass or fail (403)
 *     → Controller receives @AuthenticationPrincipal Jwt jwt
 *     → jwt.getSubject() is the userId used in business logic
 *
 * MockJwtDecoderConfig replaces the real NimbusJwtDecoder with a lookup map,
 * so no real Keycloak server is needed.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class SlotControllerSecurityTest {

    static final String DOCTOR_TOKEN  = "doctor-valid-token";
    static final String PATIENT_TOKEN = "patient-valid-token";
    static final String DOCTOR_ID     = "doctor-uuid-111";
    static final String PATIENT_ID    = "patient-uuid-222";

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    // ── Mock JwtDecoder — replaces NimbusJwtDecoder (no Keycloak needed) ────

    @TestConfiguration
    static class MockJwtDecoderConfig {

        @Bean
        @Primary
        JwtDecoder mockJwtDecoder() {
            return tokenValue -> switch (tokenValue) {
                case DOCTOR_TOKEN  -> buildJwt(tokenValue, DOCTOR_ID,  "DOCTOR");
                case PATIENT_TOKEN -> buildJwt(tokenValue, PATIENT_ID, "PATIENT");
                default -> throw new BadJwtException("Token is invalid or expired");
            };
        }

        private Jwt buildJwt(String tokenValue, String subject, String role) {
            return Jwt.withTokenValue(tokenValue)
                    .header("alg", "RS256")
                    .subject(subject)
                    .claim("realm_access", Map.of("roles", List.of(role)))
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(3600))
                    .build();
        }
    }

    // ── 401 — No token ───────────────────────────────────────────────────────

    @Test
    void noToken_onWriteEndpoint_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/slots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(slotRequest())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void noToken_onReadEndpoint_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/slots/1"))
                .andExpect(status().isUnauthorized());
    }

    // ── 401 — Invalid / expired token ────────────────────────────────────────

    @Test
    void invalidToken_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/slots")
                        .header("Authorization", "Bearer expired-or-tampered-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(slotRequest())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.success").value(false));
    }

    // ── 403 — Valid token but insufficient role ───────────────────────────────

    @Test
    void patientToken_onCreateSlot_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/slots")
                        .header("Authorization", "Bearer " + PATIENT_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(slotRequest())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Access denied: insufficient permissions"));
    }

    @Test
    void patientToken_onUpdateSlot_returns403() throws Exception {
        mockMvc.perform(put("/api/v1/slots/1")
                        .header("Authorization", "Bearer " + PATIENT_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(slotRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    void patientToken_onDeleteSlot_returns403() throws Exception {
        mockMvc.perform(delete("/api/v1/slots/1")
                        .header("Authorization", "Bearer " + PATIENT_TOKEN))
                .andExpect(status().isForbidden());
    }

    // ── 200 — Any authenticated user can read ────────────────────────────────

    @Test
    void patientToken_getSlots_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/slots")
                        .param("doctorId", DOCTOR_ID)
                        .header("Authorization", "Bearer " + PATIENT_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void doctorToken_getSlot_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/slots/99999")
                        .header("Authorization", "Bearer " + DOCTOR_TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    // ── 201 — Full end-to-end: token → filter → converter → controller → DB ─

    @Test
    void doctorToken_createSlot_fullFlow_returns201() throws Exception {
        mockMvc.perform(post("/api/v1/slots")
                        .header("Authorization", "Bearer " + DOCTOR_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(slotRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Slot created"))
                .andExpect(jsonPath("$.data.createdBy").value(DOCTOR_ID))
                .andExpect(jsonPath("$.data.doctorId").value(DOCTOR_ID))
                .andExpect(jsonPath("$.data.status").value("AVAILABLE"));
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    private SlotRequest slotRequest() {
        SlotRequest r = new SlotRequest();
        r.setDoctorId(DOCTOR_ID);
        r.setDate(LocalDate.now().plusDays(3));
        r.setStartTime(LocalTime.of(14, 0));
        r.setEndTime(LocalTime.of(15, 0));
        return r;
    }
}