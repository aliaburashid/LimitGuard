package com.example.activityservice.service;

import com.example.activityservice.client.AuthServiceClient;
import com.example.activityservice.controller.CreditRequestEventController;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CreditRequestEventServiceTest {

    private static final String PATH = "/api/credit-requests/events";
    private final CreditRequestEventService events = new CreditRequestEventService();
    private HttpServer auth;
    private MockMvc mvc;

    @BeforeEach
    void startAuthStub() throws IOException {
        auth = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        auth.createContext("/api/auth/internal/me", exchange -> {
            try (exchange) {
                String token = exchange.getRequestHeaders().getFirst("Authorization");
                String key = exchange.getRequestHeaders().getFirst("X-Internal-Service-Key");
                if (!"test-key".equals(key)) {
                    exchange.sendResponseHeaders(403, -1);
                } else if ("Bearer invalid".equals(token)) {
                    exchange.sendResponseHeaders(401, -1);
                } else {
                    long institutionId = "Bearer bank-two".equals(token) ? 2L : 1L;
                    String accountStatus = "Bearer inactive".equals(token) ? "DEACTIVATED" : "ACTIVE";
                    byte[] body = ("{\"userId\":7,\"role\":\"RELATIONSHIP_MANAGER\",\"status\":\""
                            + accountStatus + "\",\"financialInstitutionId\":" + institutionId + "}")
                            .getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, body.length);
                    exchange.getResponseBody().write(body);
                }
            }
        });
        auth.start();
        mvc = mvcWithKey("test-key");
    }

    @AfterEach
    void stopAuthStub() {
        if (auth != null) {
            auth.stop(0);
        }
    }

    @Test
    void rejectsMissingInvalidAndInactiveSessions() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        mvc.perform(get(PATH).header("Authorization", "Bearer invalid")).andExpect(status().isUnauthorized());
        mvc.perform(get(PATH).header("Authorization", "Bearer inactive")).andExpect(status().isUnauthorized());
        mvcWithKey("wrong-key").perform(get(PATH).header("Authorization", "Bearer bank-one"))
                .andExpect(status().isForbidden());
    }

    @Test
    void deniesNewStreamsWhenAuthIsUnavailable() throws Exception {
        stopAuthStub();
        auth = null;
        mvc.perform(get(PATH).header("Authorization", "Bearer bank-one"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void sendsOnlyTheExistingSsePayloadToTheCorrectInstitution() throws Exception {
        MvcResult firstBank = subscribe("bank-one");
        MvcResult secondBank = subscribe("bank-two");

        events.publishStatusUpdate(1L, 125L, "RESERVED");

        assertThat(firstBank.getResponse().getContentAsString())
                .contains(":connected\n\n",
                        "event:credit-request-status\ndata:{\"creditRequestId\":125,\"status\":\"RESERVED\"}\n\n");
        assertThat(secondBank.getResponse().getContentAsString()).isEqualTo(":connected\n\n");
    }

    @Test
    @SuppressWarnings("unchecked")
    void removesDisconnectedClientAndKeepsDeliveringToConnectedClients() throws Exception {
        MvcResult connected = subscribe("bank-one");
        SseEmitter disconnected = mock(SseEmitter.class);
        doThrow(new IOException("Browser disconnected")).when(disconnected).send(any(SseEmitter.SseEventBuilder.class));

        Map<Long, Set<SseEmitter>> subscriptions =
                (Map<Long, Set<SseEmitter>>) ReflectionTestUtils.getField(events, "emitters");
        subscriptions.get(1L).add(disconnected);
        events.publishStatusUpdate(1L, 125L, "RESERVED");
        events.publishStatusUpdate(1L, 125L, "USED");

        verify(disconnected, times(1)).send(any(SseEmitter.SseEventBuilder.class));
        assertThat(subscriptions.get(1L)).doesNotContain(disconnected);
        assertThat(connected.getResponse().getContentAsString()).contains("RESERVED", "USED");
    }

    @Test
    void deliversCounterpartyKafkaEventOnlyToItsInstitution() throws Exception {
        MvcResult firstBank = subscribe("bank-one");
        MvcResult secondBank = subscribe("bank-two");
        var consumer = new CreditRequestNotificationConsumer(
                tools.jackson.databind.json.JsonMapper.builder().build(), events);

        consumer.receiveNotification("""
                {"id":42,"financialInstitutionId":1,"eventType":"COUNTERPARTY_STATUS_UPDATED",
                 "counterpartyId":123,"status":null}
                """);
        assertThat(firstBank.getResponse().getContentAsString()).isEqualTo(":connected\n\n");

        consumer.receiveNotification("""
                {"id":43,"financialInstitutionId":1,"eventType":"COUNTERPARTY_STATUS_UPDATED",
                 "counterpartyId":123,"status":"ACTIVE"}
                """);

        assertThat(firstBank.getResponse().getContentAsString())
                .isEqualTo(":connected\n\nevent:counterparty-status\ndata:{\"counterpartyId\":123,\"status\":\"ACTIVE\"}\n\n");
        assertThat(secondBank.getResponse().getContentAsString()).isEqualTo(":connected\n\n");
    }

    @Test
    void deliversExactFacilityAmountsOnlyToTheOwningInstitution() throws Exception {
        MvcResult firstBank = subscribe("bank-one");
        MvcResult secondBank = subscribe("bank-two");
        var consumer = new CreditRequestNotificationConsumer(
                tools.jackson.databind.json.JsonMapper.builder().build(), events);
        String notification = """
                {"id":42,"financialInstitutionId":1,"eventType":"CREDIT_LIMIT_UPDATED",
                 "creditLimitId":123,"oldLimitAmount":50000.00,"newLimitAmount":9007199254740993.23}
                """;

        consumer.receiveNotification(notification.replace("50000.00", "null"));
        consumer.receiveNotification(notification.replace("50000.00", "\"50000.00\""));
        consumer.receiveNotification(notification.replace("\"creditLimitId\":123", "\"creditLimitId\":-1"));
        assertThat(firstBank.getResponse().getContentAsString()).isEqualTo(":connected\n\n");

        consumer.receiveNotification(notification);
        assertThat(firstBank.getResponse().getContentAsString()).isEqualTo(
                ":connected\n\nevent:credit-limit-updated\ndata:"
                + "{\"creditLimitId\":123,\"oldLimitAmount\":50000.00,\"newLimitAmount\":9007199254740993.23}\n\n");
        assertThat(secondBank.getResponse().getContentAsString()).isEqualTo(":connected\n\n");
    }

    @Test
    void deliversExactGroupAmountsOnlyToTheOwningInstitution() throws Exception {
        MvcResult firstBank = subscribe("bank-one");
        MvcResult secondBank = subscribe("bank-two");
        var consumer = new CreditRequestNotificationConsumer(
                tools.jackson.databind.json.JsonMapper.builder().build(), events);
        String notification = """
                {"id":42,"financialInstitutionId":1,"eventType":"GROUP_CREDIT_LIMIT_UPDATED",
                 "groupCreditLimitId":123,"corporateGroupId":456,"oldLimitAmount":100000.00,"newLimitAmount":9007199254740993.23}
                """;

        consumer.receiveNotification(notification.replace("100000.00", "null"));
        consumer.receiveNotification(notification.replace("100000.00", "\"100000.00\""));
        consumer.receiveNotification(notification.replace("\"groupCreditLimitId\":123", "\"groupCreditLimitId\":-1"));
        consumer.receiveNotification(notification.replace("\"corporateGroupId\":456", "\"corporateGroupId\":null"));
        assertThat(firstBank.getResponse().getContentAsString()).isEqualTo(":connected\n\n");

        consumer.receiveNotification(notification);
        assertThat(firstBank.getResponse().getContentAsString()).isEqualTo(
                ":connected\n\nevent:group-credit-limit-updated\ndata:"
                + "{\"groupCreditLimitId\":123,\"corporateGroupId\":456,\"oldLimitAmount\":100000.00,\"newLimitAmount\":9007199254740993.23}\n\n");
        assertThat(secondBank.getResponse().getContentAsString()).isEqualTo(":connected\n\n");
    }

    private MockMvc mvcWithKey(String key) {
        AuthServiceClient client = new AuthServiceClient("http://127.0.0.1:" + auth.getAddress().getPort(), key);
        return MockMvcBuilders.standaloneSetup(new CreditRequestEventController(client, events)).build();
    }

    private MvcResult subscribe(String token) throws Exception {
        return mvc.perform(get(PATH).header("Authorization", "Bearer " + token)
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();
    }
}
