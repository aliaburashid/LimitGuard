package com.example.creditservice.service;

import com.example.creditservice.client.AuthServiceClient;
import com.example.creditservice.dto.AuthContextResponse;
import com.example.creditservice.model.CorporateGroup;
import com.example.creditservice.model.CorporateGroupMembership;
import com.example.creditservice.model.Counterparty;
import com.example.creditservice.model.CounterpartyFinancialProfile;
import com.example.creditservice.model.CreditLimit;
import com.example.creditservice.repository.CorporateGroupMembershipRepository;
import com.example.creditservice.repository.CorporateGroupRepository;
import com.example.creditservice.repository.CounterpartyFinancialProfileRepository;
import com.example.creditservice.repository.CounterpartyRepository;
import com.example.creditservice.repository.CreditLimitRepository;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.username=expiry_test",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never",
        "limitguard.credit-request-expiry.enabled=false",
        "limitguard.notification-relay.enabled=false",
        "limitguard.exposure-snapshots.enabled=false",
        "jwt-secret=credit-ml-tests-only-signing-key-123",
        "internal-service-key=credit-ml-test-only-key"
})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@EnabledIfEnvironmentVariable(named = "CREDIT_EXPIRY_TEST_JDBC_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "CREDIT_ML_TEST_URL", matches = "http://127[.]0[.]0[.]1:[0-9]+")
class MlEndpointIntegrationTest {

    private static final String SIGNING_KEY = "credit-ml-tests-only-signing-key-123";
    private static final String PREDICTION = "/api/counterparties/{id}/risk-prediction";
    private static final String ASSESSMENT = "/api/corporate-groups/{id}/risk-assessment";
    private static final String UPSTREAM_ERROR = "Unable to obtain ML risk prediction";
    private static final long INSTITUTION = 5L;
    private static final long OTHER_INSTITUTION = 9L;
    private static final AtomicInteger upstreamCalls = new AtomicInteger();
    private static final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    private static final ObjectMapper json = new ObjectMapper();
    private static final ExecutorService workers = Executors.newCachedThreadPool();
    private static HttpServer upstream;
    private static volatile CountDownLatch parallelRequests;

    @Autowired private MockMvc mvc;
    @Autowired private CorporateGroupRepository groups;
    @Autowired private CorporateGroupMembershipRepository memberships;
    @Autowired private CounterpartyRepository counterparties;
    @Autowired private CounterpartyFinancialProfileRepository profiles;
    @Autowired private CreditLimitRepository limits;
    @MockitoBean private AuthServiceClient auth;

    @DynamicPropertySource
    static void configureIsolatedServices(DynamicPropertyRegistry properties) throws IOException {
        String database = System.getenv("CREDIT_EXPIRY_TEST_JDBC_URL");
        if (!database.matches("jdbc:postgresql://127[.]0[.]0[.]1:[0-9]{1,5}/limitguard_expiry_test")
                || database.contains(":5432/")) {
            throw new IllegalArgumentException("ML tests require dedicated loopback PostgreSQL");
        }
        properties.add("spring.datasource.url", () -> database);

        // Forward normal requests to FastAPI and simulate failures over HTTP.
        upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        upstream.setExecutor(workers);
        upstream.createContext("/predict", MlEndpointIntegrationTest::predict);
        upstream.start();
        properties.add("ml.service.url", () -> "http://127.0.0.1:" + upstream.getAddress().getPort());
    }

    @AfterAll
    static void stopUpstream() {
        if (upstream != null) {
            upstream.stop(0);
        }
        workers.shutdownNow();
    }

    @Test
    void individualPredictionMatchesTheRealFastApiResponse() throws Exception {
        long companyId = addCompany(INSTITUTION, 8, null);
        var expected = predictDirectly(8);

        call(PREDICTION, companyId, login("RISK_OFFICER", INSTITUTION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.probability").value(expected.path("probability").asDouble()))
                .andExpect(jsonPath("$.riskLevel").value(expected.path("riskLevel").asText()))
                .andExpect(jsonPath("$.modelVersion").value(expected.path("modelVersion").asText()));
    }

    @Test
    void authorizationStopsUnauthorizedRequestsBeforeCallingFastApi() throws Exception {
        CorporateGroup group = addGroup();
        long companyId = addCompany(INSTITUTION, 8, group);
        int before = upstreamCalls.get();
        String manager = login("RELATIONSHIP_MANAGER", INSTITUTION);
        String otherBank = login("RISK_OFFICER", OTHER_INSTITUTION);

        mvc.perform(post(PREDICTION, companyId)).andExpect(status().isUnauthorized());
        mvc.perform(post(ASSESSMENT, group.getId())).andExpect(status().isUnauthorized());
        call(PREDICTION, companyId, manager).andExpect(status().isForbidden());
        call(ASSESSMENT, group.getId(), manager).andExpect(status().isForbidden());
        call(PREDICTION, companyId, otherBank).andExpect(status().isForbidden());
        call(ASSESSMENT, group.getId(), otherBank).andExpect(status().isNotFound());
        assertThat(upstreamCalls.get()).isEqualTo(before);
    }

    @Test
    void groupRunsFourRequestsTogetherAndKeepsSuccessfulResultsWhenOneFails() throws Exception {
        CorporateGroup group = addGroup();
        long first = addCompany(INSTITUTION, 11, group);
        addCompany(INSTITUTION, 12, group);
        addCompany(INSTITUTION, 13, group);
        long failed = addCompany(INSTITUTION, 14, group);
        long outside = addCompany(OTHER_INSTITUTION, 8, group);
        int before = upstreamCalls.get();
        var expected = predictDirectly(11);
        var requests = new CountDownLatch(4);
        parallelRequests = requests;
        try {
            String response = call(ASSESSMENT, group.getId(), login("RISK_OFFICER", INSTITUTION))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(4))
                    .andExpect(jsonPath("$[?(@.successful == true)]").value(hasSize(3)))
                    .andExpect(jsonPath("$[?(@.counterpartyId == " + outside + ")]").isEmpty())
                    .andExpect(jsonPath("$[?(@.counterpartyId == " + failed + ")].successful").value(contains(false)))
                    .andExpect(jsonPath("$[?(@.counterpartyId == " + failed + ")].errorMessage")
                            .value(contains("Risk assessment failed")))
                    .andReturn().getResponse().getContentAsString();
            assertThat(json.readTree(response).values()).filteredOn(row -> row.path("counterpartyId").asLong() == first)
                    .singleElement().satisfies(row -> {
                        assertThat(row.path("probability").decimalValue())
                                .isEqualByComparingTo(expected.path("probability").decimalValue());
                        assertThat(row.path("riskLevel").asText()).isEqualTo(expected.path("riskLevel").asText());
                    });
        } finally {
            parallelRequests = null;
        }
        assertThat(requests.getCount()).isZero();
        assertThat(upstreamCalls.get() - before).isEqualTo(4);
    }

    @Test
    void groupReturnsSuccessfulPredictionsForAllAuthorizedCompanies() throws Exception {
        CorporateGroup group = addGroup();
        addCompany(INSTITUTION, 8, group);
        addCompany(INSTITUTION, 9, group);

        call(ASSESSMENT, group.getId(), login("RISK_OFFICER", INSTITUTION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.successful == true)]").value(hasSize(2)));
    }

    @Test
    void fastApiReturnsValidationErrorAndCreditReturnsBadGateway() throws Exception {
        // The real FastAPI model rejects a finite value outside its scoring bins.
        var rejected = forward(json.writeValueAsBytes(financialRatios(-999999)));
        assertThat(rejected.statusCode()).isEqualTo(422);
        assertThat(json.readTree(rejected.body()).path("detail").asText()).contains("outside the model");
        long companyId = addCompany(INSTITUTION, -999999, null);
        call(PREDICTION, companyId, login("RISK_OFFICER", INSTITUTION))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value(UPSTREAM_ERROR));
    }

    @Test
    void upstreamReadTimeoutReturnsBadGateway() throws Exception {
        long companyId = addCompany(INSTITUTION, 21, null);
        long startedAt = System.nanoTime();
        call(PREDICTION, companyId, login("RISK_OFFICER", INSTITUTION))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value(UPSTREAM_ERROR));
        assertThat(Duration.ofNanos(System.nanoTime() - startedAt)).isGreaterThanOrEqualTo(Duration.ofSeconds(5));
    }

    @Test
    void unavailableUpstreamReturnsBadGateway() throws Exception {
        // Closing the response socket simulates a server dropping its connection.
        long companyId = addCompany(INSTITUTION, 22, null);
        call(PREDICTION, companyId, login("RISK_OFFICER", INSTITUTION))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value(UPSTREAM_ERROR));
    }

    private static void predict(HttpExchange exchange) throws IOException {
        try (exchange) {
            upstreamCalls.incrementAndGet();
            byte[] payload = exchange.getRequestBody().readAllBytes();
            var body = json.readTree(payload);
            int assets = body.path("returnOnAssets").asInt();
            var expected = financialRatios(assets);
            assertThat(body.size()).isEqualTo(expected.size());
            expected.forEach((name, amount) -> assertThat(body.path(name).decimalValue()).isEqualByComparingTo(amount));
            assertThat(exchange.getRequestMethod()).isEqualTo("POST");
            assertThat(exchange.getRequestHeaders().getFirst("Content-Type")).startsWith("application/json");

            if (assets == 22) {
                return;
            }
            if (assets == 21) {
                Thread.sleep(8000);
            }
            var requests = parallelRequests;
            if (assets >= 11 && assets <= 14 && requests != null) {
                requests.countDown();
                if (!requests.await(3, TimeUnit.SECONDS)) {
                    send(exchange, 503, "{}".getBytes(StandardCharsets.UTF_8));
                    return;
                }
            }
            if (assets == 14) {
                send(exchange, 500, "{}".getBytes(StandardCharsets.UTF_8));
                return;
            }
            var result = forward(payload);
            send(exchange, result.statusCode(), result.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static void send(HttpExchange exchange, int status, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
    }

    private static HttpResponse<byte[]> forward(byte[] body) throws IOException, InterruptedException {
        return http.send(HttpRequest.newBuilder(URI.create(System.getenv("CREDIT_ML_TEST_URL") + "/predict"))
                        .timeout(Duration.ofSeconds(4)).header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build(),
                HttpResponse.BodyHandlers.ofByteArray());
    }

    private static JsonNode predictDirectly(int assets) throws Exception {
        var response = forward(json.writeValueAsBytes(financialRatios(assets)));
        assertThat(response.statusCode()).isEqualTo(200);
        return json.readTree(response.body());
    }

    private static Map<String, BigDecimal> financialRatios(int assets) {
        return Map.of("returnOnAssets", BigDecimal.valueOf(assets), "profitMargin", new BigDecimal("12"),
                "fixedAssetTurnover", new BigDecimal("1.5"), "debtToTotalCapitalPercent", new BigDecimal("40"),
                "interestCoverage", new BigDecimal("5"), "totalDebtToEbitda", new BigDecimal("2.5"));
    }

    private CorporateGroup addGroup() {
        CorporateGroup group = new CorporateGroup();
        group.setName("ML test group " + UUID.randomUUID());
        return groups.save(group);
    }

    private long addCompany(long institution, int assets, CorporateGroup group) {
        Counterparty counterparty = new Counterparty();
        counterparty.setName("ML test company " + UUID.randomUUID());
        counterparty = counterparties.save(counterparty);

        CreditLimit limit = new CreditLimit();
        limit.setCounterparty(counterparty);
        limit.setFinancialInstitutionId(institution);
        limit.setLimitAmount(new BigDecimal("100000"));
        limit.setUsedAmount(BigDecimal.ZERO);
        limit.setReservedAmount(BigDecimal.ZERO);
        limits.save(limit);

        CounterpartyFinancialProfile profile = new CounterpartyFinancialProfile();
        profile.setCounterparty(counterparty);
        var ratios = financialRatios(assets);
        profile.setReturnOnAssets(ratios.get("returnOnAssets"));
        profile.setProfitMargin(ratios.get("profitMargin"));
        profile.setFixedAssetTurnover(ratios.get("fixedAssetTurnover"));
        profile.setDebtToTotalCapitalPercent(ratios.get("debtToTotalCapitalPercent"));
        profile.setInterestCoverage(ratios.get("interestCoverage"));
        profile.setTotalDebtToEbitda(ratios.get("totalDebtToEbitda"));
        profile.setFinancialYearEnd(LocalDate.of(2025, 12, 31));
        profiles.save(profile);

        if (group != null) {
            CorporateGroupMembership membership = new CorporateGroupMembership();
            membership.setCorporateGroup(group);
            membership.setCounterparty(counterparty);
            memberships.save(membership);
        }
        return counterparty.getId();
    }

    private String login(String role, long institution) {
        String token = Jwts.builder().id(UUID.randomUUID().toString()).subject("ml-test@example.test")
                .expiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(Keys.hmacShaKeyFor(SIGNING_KEY.getBytes(StandardCharsets.UTF_8))).compact();
        when(auth.getAuthContext(token)).thenReturn(new AuthContextResponse(7L, role, "ACTIVE", institution));
        return "Bearer " + token;
    }

    private ResultActions call(String path, long id, String authorization) throws Exception {
        return mvc.perform(post(path, id).header("Authorization", authorization));
    }
}
