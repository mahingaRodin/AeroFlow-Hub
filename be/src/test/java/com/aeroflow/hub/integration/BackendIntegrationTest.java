package com.aeroflow.hub.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aeroflow.hub.model.User;
import com.aeroflow.hub.repository.QuarantineEntryRepository;
import com.aeroflow.hub.repository.UserRepository;
import com.aeroflow.hub.service.InitialAdminProvisioner;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.util.Base64;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "app.bootstrap.admin.enabled=true",
        "app.bootstrap.admin.username=integration-admin",
        "app.bootstrap.admin.email=integration-admin@example.test",
        "app.bootstrap.admin.password=IntegrationAdminPassword123",
        "app.security.jwt.expiration=900000",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.baseline-on-migrate=false"
})
@AutoConfigureMockMvc
class BackendIntegrationTest {

    private static final String ADMIN_PASSWORD = "IntegrationAdminPassword123";
    private static final String TEST_JWT_SECRET =
            "7c2M3k9zX4s8A1b6C0d5E9f2G7h1J4m8L3n6P0q5R9t=";
    private static final String TEST_MFA_ENCRYPTION_KEY =
            Base64.getEncoder().encodeToString(new byte[32]);

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private QuarantineEntryRepository quarantineRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private InitialAdminProvisioner initialAdminProvisioner;

    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("JWT_SECRET_BASE64", () -> TEST_JWT_SECRET);
        registry.add("APP_SECURITY_JWT_SECRET", () -> TEST_JWT_SECRET);
        registry.add("MFA_ENCRYPTION_KEY_BASE64", () -> TEST_MFA_ENCRYPTION_KEY);
    }

    @Test
    void migratesPostgresBootstrapsAdminAuthenticatesAndQuarantinesInvalidInput() throws Exception {
        User admin = userRepository.findByUsername("integration-admin").orElseThrow();
        assertEquals("integration-admin@example.test", admin.getEmail());
        assertTrue(passwordEncoder.matches(ADMIN_PASSWORD, admin.getPassword()));
        assertNotEquals(ADMIN_PASSWORD, admin.getPassword());

        mockMvc.perform(get("/api/v1/auth/mfa/status"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/mfa/totp/setup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(new PasswordRequest(ADMIN_PASSWORD))))
                .andExpect(status().isUnauthorized());

        initialAdminProvisioner.run(new DefaultApplicationArguments());
        assertEquals(1, userRepository.count());

        mockMvc.perform(get("/api/v1/ingestion/quarantine"))
                .andExpect(status().isUnauthorized());

        JsonNode loginResponse = objectMapper.readTree(mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(
                                new LoginRequest("integration-admin", ADMIN_PASSWORD))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray());
        String token = loginResponse.path("accessToken").asText();
        assertNotNull(token);
        assertTrue(!token.isBlank());

        mockMvc.perform(get("/api/v1/travel-clearance-requests/pending")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        String malformedPayload = "1HEADER\nXINVALID";
        JsonNode ingestionResponse = objectMapper.readTree(mockMvc.perform(post("/api/v1/ingestion/ssim")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(new IngestionRequest(malformedPayload))))
                .andExpect(status().isAccepted())
                .andReturn()
                .getResponse()
                .getContentAsByteArray());

        assertTrue(ingestionResponse.path("quarantined").asBoolean());
        assertEquals(1, quarantineRepository.count());
        String quarantineId = ingestionResponse.path("quarantineId").asText();

        JsonNode detail = objectMapper.readTree(mockMvc.perform(get("/api/v1/ingestion/quarantine/{id}", quarantineId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray());

        assertEquals(malformedPayload, detail.path("rawPayload").asText());
        assertEquals("integration-admin", detail.path("submittedBy").asText());
        assertEquals(1, detail.path("issues").size());
    }

    private record LoginRequest(String username, String password) {
    }

    private record PasswordRequest(String currentPassword) {
    }

    private record IngestionRequest(String payload) {
    }
}
