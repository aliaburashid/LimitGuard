package com.example.authservice.documentation;

import com.example.authservice.service.EmailService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.method.HandlerMethod;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:auth_openapi;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never",
        "jwt-secret=auth-openapi-tests-only-signing-key",
        "jwt-expiration-ms=60000"
})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class OpenApiSecurityTest {

    private static final String BEARER = "Bearer Authentication";
    private static final String USERS = "/api/auth/users";
    private static final Set<String> PUBLIC_OPERATIONS = Set.of(
            "POST " + USERS + "/register",
            "GET " + USERS + "/verify-email",
            "POST " + USERS + "/login",
            "POST " + USERS + "/forgot-password",
            "POST " + USERS + "/reset-password",
            "POST " + USERS + "/verification-code/request",
            "POST " + USERS + "/verification-code/verify",
            "POST " + USERS + "/verification-code/resend",
            "POST " + USERS + "/verification-code/change-email",
            "POST " + USERS + "/password-reset-code/request",
            "POST " + USERS + "/password-reset-code/resend",
            "POST " + USERS + "/password-reset-code/verify",
            "GET /api/auth/sso/providers",
            "POST /api/auth/sso/exchange",
            "GET /api/financial-institutions/directory"
    );
    private static final Set<String> PROTECTED_OPERATIONS = Set.of(
            "PATCH " + USERS + "/change-password",
            "GET " + USERS + "/profile",
            "PATCH " + USERS + "/profile",
            "GET " + USERS + "/profile-picture",
            "PATCH " + USERS + "/profile-picture",
            "PATCH " + USERS + "/{userId}/role",
            "PATCH " + USERS + "/{userId}/deactivate",
            "POST /api/financial-institutions",
            "PUT /api/financial-institutions/{institutionId}"
    );

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @MockitoBean
    private EmailService emailService;

    @Test
    void openApiSecurityMatchesAnonymousAccessForEveryOperation() throws Exception {
        String body = mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode document = json.readTree(body);
        JsonNode bearer = document.path("components").path("securitySchemes").path(BEARER);
        assertThat(bearer.path("type").asText()).isEqualTo("http");
        assertThat(bearer.path("scheme").asText()).isEqualTo("bearer");
        assertThat(bearer.path("bearerFormat").asText()).isEqualTo("JWT");

        Set<String> documentedPublic = new HashSet<>();
        Set<String> documentedProtected = new HashSet<>();
        for (var path : document.path("paths").properties()) {
            for (var operation : path.getValue().properties()) {
                String method = operation.getKey().toUpperCase(Locale.ROOT);
                String name = method + " " + path.getKey();
                JsonNode security = operation.getValue().has("security")
                        ? operation.getValue().get("security") : document.path("security");
                assertThat(security.isArray()).as(name).isTrue();

                // Invalid bodies reach public controllers without creating accounts or sending email.
                String url = path.getKey().replace("{userId}", "123").replace("{institutionId}", "123");
                var result = mvc.perform(request(HttpMethod.valueOf(method), url)
                                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                        .andReturn();
                if (security.isEmpty()) {
                    documentedPublic.add(name);
                    // SSO exchange can reject a missing handoff session after Spring Security admits the request.
                    assertThat(result.getHandler()).as(name).isInstanceOf(HandlerMethod.class);
                } else {
                    documentedProtected.add(name);
                    assertThat(security.size()).as(name).isEqualTo(1);
                    assertThat(security.get(0).has(BEARER)).as(name).isTrue();
                    assertThat(result.getResponse().getStatus()).as(name).isEqualTo(401);
                    assertThat(result.getHandler()).as(name).isNull();
                }
            }
        }
        assertThat(documentedPublic).containsExactlyInAnyOrderElementsOf(PUBLIC_OPERATIONS);
        assertThat(documentedProtected).containsExactlyInAnyOrderElementsOf(PROTECTED_OPERATIONS);
    }
}
