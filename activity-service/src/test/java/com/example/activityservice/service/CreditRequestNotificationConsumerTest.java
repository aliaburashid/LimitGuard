package com.example.activityservice.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(OutputCaptureExtension.class)
class CreditRequestNotificationConsumerTest {

    private static final String NOTIFICATION = """
            {
                "id": 4294967296,
                "financialInstitutionId": 5,
                "creditRequestId": 125,
                "requesterId": 15,
                "status": "REJECTED",
                "eventType": "CREDIT_REQUEST_REJECTED",
                "reason": "Sensitive test reason",
                "createdAt": "2026-11-14T09:00:00",
                "publishedAt": null
            }
            """;

    private final CreditRequestEventService events = mock(CreditRequestEventService.class);
    private final CreditRequestNotificationConsumer consumer =
            new CreditRequestNotificationConsumer(JsonMapper.builder().build(), events);

    @Test
    void logsOnlyTheNotificationIdentifiersAndType(CapturedOutput output) {
        consumer.receiveNotification(NOTIFICATION);
        verify(events).publishStatusUpdate(5L, 125L, "REJECTED");
        verifyNoMoreInteractions(events);

        assertThat(output.getAll())
                .contains("Event ID=4294967296, credit request ID=125, event type=CREDIT_REQUEST_REJECTED")
                .doesNotContain("Sensitive test reason", "requesterId", "financialInstitutionId");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"not-json confidential-marker", "null", "{}", "{\"id\":\"not-a-long\"}", "[]"})
    void skipsMalformedMessagesAndProcessesTheNextEvent(String message, CapturedOutput output) {
        consumer.receiveNotification(message);
        consumer.receiveNotification(NOTIFICATION);
        verify(events).publishStatusUpdate(5L, 125L, "REJECTED");
        verifyNoMoreInteractions(events);

        assertThat(output.getAll())
                .contains("Skipping malformed credit request notification", "Event ID=4294967296")
                .doesNotContain("confidential-marker", "Sensitive test reason");
    }

    @Test
    void skipsJsonWithExtraTrailingContent(CapturedOutput output) {
        consumer.receiveNotification(NOTIFICATION + "{}");
        verifyNoMoreInteractions(events);

        assertThat(output.getAll())
                .contains("Skipping malformed credit request notification")
                .doesNotContain("Event ID=4294967296");
    }
}
