package com.example.activityservice.service;

import com.example.activityservice.dto.CreditRequestNotification;
import com.example.activityservice.repository.CreditRequestEmailDeliveryRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class CreditRequestEmailConsumerTest {

    private final CreditRequestEmailDeliveryRepository deliveries = mock(CreditRequestEmailDeliveryRepository.class);
    private final CreditRequestEmailConsumer consumer = new CreditRequestEmailConsumer(
            JsonMapper.builder().build(), deliveries);

    @ParameterizedTest
    @CsvSource({
            "CREDIT_REQUEST_APPROVED, RESERVED",
            "CREDIT_REQUEST_REJECTED, REJECTED",
            "CREDIT_REQUEST_EXPIRED, EXPIRED"
    })
    void queuesOnlyEligibleCreditEvents(String eventType, String status) {
        consumer.receiveNotification(notification(eventType, status));

        var event = ArgumentCaptor.forClass(CreditRequestNotification.class);
        verify(deliveries).queueDelivery(event.capture());
        assertThat(event.getValue().getId()).isEqualTo(4294967296L);
        assertThat(event.getValue().getEventType()).isEqualTo(eventType);
        assertThat(event.getValue().getRequesterId()).isEqualTo(15L);
        assertThat(event.getValue().getFinancialInstitutionId()).isEqualTo(5L);
        assertThat(event.getValue().getCreditRequestId()).isEqualTo(125L);
    }

    @ParameterizedTest
    @CsvSource({
            "CREDIT_REQUEST_CREATED, RESERVED",
            "CREDIT_REQUEST_USED, USED",
            "CREDIT_REQUEST_CANCELLED, CANCELLED",
            "CREDIT_REQUEST_UNKNOWN, RESERVED",
            "CREDIT_REQUEST_APPROVED, PENDING_APPROVAL",
            "COUNTERPARTY_STATUS_UPDATED, ACTIVE",
            "CREDIT_LIMIT_UPDATED, ACTIVE",
            "GROUP_CREDIT_LIMIT_UPDATED, ACTIVE"
    })
    void ignoresUnrelatedEventsAndInconsistentStatuses(String eventType, String status) {
        consumer.receiveNotification(notification(eventType, status));
        verifyNoInteractions(deliveries);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"not json", "null", "[]", "{}",
            "{\"eventType\":\"CREDIT_REQUEST_EXPIRED\",\"status\":\"EXPIRED\",\"id\":null}"})
    void skipsMalformedMessages(String message) {
        consumer.receiveNotification(message);
        verifyNoInteractions(deliveries);
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "\"15\"", "0", "1.5", "9223372036854775808"})
    void skipsInvalidRequesterIds(String requesterId) {
        consumer.receiveNotification(notification("CREDIT_REQUEST_APPROVED", "RESERVED")
                .replace("\"requesterId\":15", "\"requesterId\":" + requesterId));
        verifyNoInteractions(deliveries);
    }

    private String notification(String eventType, String status) {
        return """
                {"id":4294967296,"financialInstitutionId":5,"creditRequestId":125,"requesterId":15,
                 "status":"%s","eventType":"%s","reason":null,"createdAt":"2026-11-14T09:00:00","publishedAt":null}
                """.formatted(status, eventType);
    }
}
