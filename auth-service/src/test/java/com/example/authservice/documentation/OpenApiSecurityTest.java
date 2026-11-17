package com.example.authservice.documentation;

import com.example.authservice.enums.UserStatus;
import com.example.authservice.model.FinancialInstitution;
import com.example.authservice.model.User;
import com.example.authservice.repository.FinancialInstitutionRepository;
import com.example.authservice.repository.UserRepository;
import com.example.authservice.security.JWTUtils;
import com.example.authservice.security.MyUserDetails;
import com.example.authservice.service.EmailService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.method.HandlerMethod;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:auth_openapi;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never",
        "jwt-secret=auth-openapi-tests-only-signing-key",
        "jwt-expiration-ms=60000",
        "internal-service-key=" + OpenApiSecurityTest.INTERNAL_SERVICE_KEY,
        "activity-service.notification-recipient-key=" + OpenApiSecurityTest.ACTIVITY_RECIPIENT_KEY
})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class OpenApiSecurityTest {

    static final String INTERNAL_SERVICE_KEY = "openapi-test-service-key";
    private static final String INTERNAL_KEY_HEADER = "X-Internal-Service-Key";
    private static final String INTERNAL = "/api/auth/internal";
    static final String ACTIVITY_RECIPIENT_KEY = "activity-recipient-test-key";
    private static final String ACTIVITY_KEY_HEADER = "X-Activity-Service-Key";
    private static final String RECIPIENT_PATH = INTERNAL + "/notification-recipient";
    private static final String ACTIVITY_SCHEME = "Activity Recipient Key";
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
            "PUT /api/financial-institutions/{institutionId}",
            "GET " + INTERNAL + "/me",
            "GET " + INTERNAL + "/users/{userId}/display"
    );

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JWTUtils jwt;

    @Autowired
    private UserRepository users;

    @Autowired
    private FinancialInstitutionRepository institutions;

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

        JsonNode activityScheme = document.path("components").path("securitySchemes").path(ACTIVITY_SCHEME);
        assertThat(activityScheme.path("type").asText()).isEqualTo("apiKey");
        assertThat(activityScheme.path("in").asText()).isEqualTo("header");
        assertThat(activityScheme.path("name").asText()).isEqualTo(ACTIVITY_KEY_HEADER);
        boolean recipientDocumented = false;
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
                if (name.equals("GET " + RECIPIENT_PATH)) {
                    recipientDocumented = true;
                    assertThat(security.size()).isEqualTo(1);
                    assertThat(security.get(0).has(ACTIVITY_SCHEME)).isTrue();
                    assertThat(result.getResponse().getStatus()).isEqualTo(401);
                    assertThat(result.getHandler()).isNull();
                } else if (security.isEmpty()) {
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
        assertThat(recipientDocumented).isTrue();
        assertThat(documentedPublic).containsExactlyInAnyOrderElementsOf(PUBLIC_OPERATIONS);
        assertThat(documentedProtected).containsExactlyInAnyOrderElementsOf(PROTECTED_OPERATIONS);
    }

    @Test
    void internalOperationsRequireBothJwtAndServiceKey() throws Exception {
        FinancialInstitution institution = new FinancialInstitution();
        institution.setName("OpenAPI test institution");
        Long institutionId = institutions.save(institution).getId();

        User user = new User();
        user.setFirstName("OpenAPI");
        user.setLastName("Fixture");
        user.setEmail("openapi-fixture@example.test");
        user.setPassword("unused-test-value");
        user.setEmailVerified(true);
        user.setFinancialInstitution(institution);
        Long userId = users.save(user).getId();
        String authorization = "Bearer " + jwt.generateJwtToken(new MyUserDetails(user));

        for (String path : Set.of(INTERNAL + "/me", INTERNAL + "/users/" + userId + "/display")) {
            mvc.perform(get(path).header(INTERNAL_KEY_HEADER, INTERNAL_SERVICE_KEY))
                    .andExpect(status().isUnauthorized());
            mvc.perform(get(path).header(INTERNAL_KEY_HEADER, INTERNAL_SERVICE_KEY)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer invalid"))
                    .andExpect(status().isUnauthorized());
            mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, authorization))
                    .andExpect(status().isForbidden());
            mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, authorization)
                            .header(INTERNAL_KEY_HEADER, "incorrect-test-key"))
                    .andExpect(status().isForbidden());
            mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, authorization)
                            .header(INTERNAL_KEY_HEADER, INTERNAL_SERVICE_KEY))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userId").value(userId))
                    .andExpect(jsonPath("$.financialInstitutionId").value(institutionId));
        }
    }

    @Test
    void activityCanReadOnlyAnActiveVerifiedRequestersEmail() throws Exception {
        User requester = saveRecipient("eligible", UserStatus.ACTIVE, true);
        Long institutionId = requester.getFinancialInstitution().getId();
        var result = mvc.perform(recipientLookup(requester.getId(), institutionId)
                        .header(ACTIVITY_KEY_HEADER, ACTIVITY_RECIPIENT_KEY))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andReturn();
        JsonNode response = json.readTree(result.getResponse().getContentAsString());
        assertThat(response.propertyNames()).containsExactly("email");
        assertThat(response.path("email").asText()).isEqualTo(requester.getEmail());

        User otherBank = saveRecipient("other-bank", UserStatus.ACTIVE, true);
        mvc.perform(recipientLookup(requester.getId(), otherBank.getFinancialInstitution().getId())
                        .header(ACTIVITY_KEY_HEADER, ACTIVITY_RECIPIENT_KEY))
                .andExpect(status().isNotFound());
        mvc.perform(recipientLookup(Long.MAX_VALUE, institutionId).header(ACTIVITY_KEY_HEADER, ACTIVITY_RECIPIENT_KEY))
                .andExpect(status().isNotFound());

        User inactive = saveRecipient("inactive", UserStatus.DEACTIVATED, true);
        User unverified = saveRecipient("unverified", UserStatus.ACTIVE, false);
        for (User ineligible : Set.of(inactive, unverified)) {
            mvc.perform(recipientLookup(ineligible.getId(), ineligible.getFinancialInstitution().getId())
                            .header(ACTIVITY_KEY_HEADER, ACTIVITY_RECIPIENT_KEY))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    void recipientLookupRejectsUserJwtAndOtherServiceCredentials() throws Exception {
        User requester = saveRecipient("credential-test", UserStatus.ACTIVE, true);
        Long institutionId = requester.getFinancialInstitution().getId();
        String authorization = "Bearer " + jwt.generateJwtToken(new MyUserDetails(requester));

        mvc.perform(recipientLookup(requester.getId(), institutionId)).andExpect(status().isUnauthorized());
        mvc.perform(recipientLookup(requester.getId(), institutionId).header(ACTIVITY_KEY_HEADER, "wrong-key"))
                .andExpect(status().isUnauthorized());
        mvc.perform(recipientLookup(requester.getId(), institutionId).header(ACTIVITY_KEY_HEADER, INTERNAL_SERVICE_KEY))
                .andExpect(status().isUnauthorized());
        mvc.perform(recipientLookup(requester.getId(), institutionId).header(INTERNAL_KEY_HEADER, INTERNAL_SERVICE_KEY))
                .andExpect(status().isUnauthorized());
        mvc.perform(recipientLookup(requester.getId(), institutionId).header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isForbidden());
    }

    private MockHttpServletRequestBuilder recipientLookup(Long requesterId, Long institutionId) {
        return get(RECIPIENT_PATH).param("requesterId", requesterId.toString())
                .param("financialInstitutionId", institutionId.toString());
    }

    private User saveRecipient(String name, UserStatus status, boolean verified) {
        FinancialInstitution institution = new FinancialInstitution();
        institution.setName("Recipient test " + name);
        institution = institutions.save(institution);
        User user = new User();
        user.setFirstName("Recipient");
        user.setLastName("Fixture");
        user.setEmail(name + "@example.test");
        user.setPassword("unused-test-value");
        user.setFinancialInstitution(institution);
        user.setStatus(status);
        user.setEmailVerified(verified);
        return users.save(user);
    }
}
