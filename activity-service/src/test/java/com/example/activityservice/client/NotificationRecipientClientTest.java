package com.example.activityservice.client;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientException;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationRecipientClientTest {

    @Test
    void sendsBothIdsAndOnlyTheDedicatedCredential() throws Exception {
        HttpServer auth = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        auth.createContext("/api/auth/internal/notification-recipient", exchange -> {
            try (exchange) {
                if (!"GET".equals(exchange.getRequestMethod())
                        || !"test-key".equals(exchange.getRequestHeaders().getFirst("X-Activity-Service-Key"))
                        || exchange.getRequestHeaders().getFirst("Authorization") != null) {
                    exchange.sendResponseHeaders(403, -1);
                    return;
                }
                if (!"requesterId=15&financialInstitutionId=5".equals(exchange.getRequestURI().getQuery())) {
                    exchange.sendResponseHeaders(404, -1);
                    return;
                }
                byte[] body = "{\"email\":\"requester@example.test\"}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            }
        });
        auth.start();
        var client = new NotificationRecipientClient("http://127.0.0.1:" + auth.getAddress().getPort(), "test-key");
        try {
            assertThat(client.findRecipient(15L, 5L)).contains("requester@example.test");
            assertThat(client.findRecipient(15L, 9L)).isEmpty();
        } finally {
            auth.stop(0);
        }
        assertThatThrownBy(() -> client.findRecipient(15L, 5L)).isInstanceOf(RestClientException.class);
    }
}
