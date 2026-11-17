package com.example.creditservice.documentation;

import com.example.creditservice.client.AuthServiceClient;
import com.example.creditservice.dto.AuthContextResponse;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.username=expiry_test",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never",
        "limitguard.credit-request-expiry.enabled=false",
        "limitguard.notification-relay.enabled=false",
        "limitguard.exposure-snapshots.enabled=false",
        "jwt-secret=" + OpenApiSecurityTest.TEST_SIGNING_KEY,
        "internal-service-key=credit-openapi-test-only-key"
})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@EnabledIfEnvironmentVariable(named = "CREDIT_EXPIRY_TEST_JDBC_URL", matches = ".+")
class OpenApiSecurityTest {

    static final String TEST_SIGNING_KEY = "credit-openapi-test-only-signing-key";
    private static final String BEARER = "Bearer Authentication";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @MockitoBean
    private AuthServiceClient auth;

    @DynamicPropertySource
    static void useDisposablePostgres(DynamicPropertyRegistry properties) {
        String url = System.getenv("CREDIT_EXPIRY_TEST_JDBC_URL");
        // Only use the same dedicated database allowed by the expiry tests.
        if (!url.matches("jdbc:postgresql://127[.]0[.]0[.]1:[0-9]{1,5}/limitguard_expiry_test")
                || url.contains(":5432/")) {
            throw new IllegalArgumentException("OpenAPI tests require dedicated loopback PostgreSQL");
        }
        properties.add("spring.datasource.url", () -> url);
    }

    @Test
    void swaggerIsPublicAndEveryCreditOperationRequiresBearerAuthentication() throws Exception {
        mvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs/swagger-config")).andExpect(status().isOk());

        String body = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode document = json.readTree(body);
        JsonNode scheme = document.path("components").path("securitySchemes").path(BEARER);
        assertThat(scheme.path("type").asText()).isEqualTo("http");
        assertThat(scheme.path("scheme").asText()).isEqualTo("bearer");
        assertThat(scheme.path("bearerFormat").asText()).isEqualTo("JWT");

        int operationCount = 0;
        for (var path : document.path("paths").properties()) {
            for (var operation : path.getValue().properties()) {
                String method = operation.getKey().toUpperCase(Locale.ROOT);
                String operationName = method + " " + path.getKey();
                JsonNode security = operation.getValue().has("security")
                        ? operation.getValue().get("security") : document.path("security");
                assertThat(security.isArray()).as(operationName).isTrue();
                assertThat(security.size()).as(operationName).isEqualTo(1);
                assertThat(security.get(0).has(BEARER)).as(operationName).isTrue();

                String url = path.getKey().replaceAll("\\{[^}]+}", "1");
                mvc.perform(request(HttpMethod.valueOf(method), url))
                        .andExpect(status().isUnauthorized());
                operationCount++;
            }
        }
        assertThat(operationCount).isEqualTo(42);
    }

    @Test
    void bearerTokenAuthenticatesWithoutChangingAuditRoleRestriction() throws Exception {
        mvc.perform(get("/api/audit-logs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid"))
                .andExpect(status().isUnauthorized());

        String jwt = Jwts.builder()
                .subject("openapi@example.test")
                .expiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(TEST_SIGNING_KEY.getBytes(StandardCharsets.UTF_8)))
                .compact();

        when(auth.getAuthContext(jwt)).thenReturn(new AuthContextResponse(7L, "RELATIONSHIP_MANAGER", "ACTIVE", 5L));
        mvc.perform(get("/api/audit-logs").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt))
                .andExpect(status().isForbidden());

        when(auth.getAuthContext(jwt)).thenReturn(new AuthContextResponse(7L, "ADMIN", "ACTIVE", 5L));
        mvc.perform(get("/api/audit-logs").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt))
                .andExpect(status().isOk());
    }
}
