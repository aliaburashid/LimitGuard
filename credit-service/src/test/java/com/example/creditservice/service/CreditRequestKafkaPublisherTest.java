package com.example.creditservice.service;

import com.example.creditservice.model.NotificationOutbox;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CreditRequestKafkaPublisherTest {

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @SuppressWarnings("unchecked")
    void publishesOriginalJsonAndReturnsKafkaAcknowledgment(boolean includeOptionalValues) {
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        var objectMapper = JsonMapper.builder().build();
        var publisher = new CreditRequestKafkaPublisher(kafkaTemplate, objectMapper);
        var acknowledgment = new CompletableFuture<SendResult<String, String>>();
        when(kafkaTemplate.send(eq("credit-request-events"), eq("125"), anyString()))
                .thenReturn(acknowledgment);

        NotificationOutbox event = new NotificationOutbox();
        event.setId(4294967296L);
        event.setFinancialInstitutionId(5L);
        event.setCreditRequestId(125L);
        event.setRequesterId(15L);
        event.setStatus("REJECTED");
        event.setEventType("CREDIT_REQUEST_REJECTED");
        event.setCreatedAt(LocalDateTime.of(2026, 11, 14, 9, 0));
        if (includeOptionalValues) {
            event.setReason("Test rejection reason");
            event.setPublishedAt(LocalDateTime.of(2026, 11, 14, 9, 1, 2, 123456000));
        }

        assertThat(publisher.publish(event)).isSameAs(acknowledgment);
        var payload = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("credit-request-events"), eq("125"), payload.capture());
        var json = objectMapper.readTree(payload.getValue());
        // Compare all original fields and values, including optional nulls and timestamp formatting.
        var expected = objectMapper.readTree("""
                {
                    "id": 4294967296,
                    "financialInstitutionId": 5,
                    "creditRequestId": 125,
                    "requesterId": 15,
                    "status": "REJECTED",
                    "eventType": "CREDIT_REQUEST_REJECTED",
                    "reason": %s,
                    "createdAt": "2026-11-14T09:00:00",
                    "publishedAt": %s
                }
                """.formatted(
                includeOptionalValues ? "\"Test rejection reason\"" : "null",
                includeOptionalValues ? "\"2026-11-14T09:01:02.123456\"" : "null"));
        assertThat(json).isEqualTo(expected);
    }

    @Test
    @SuppressWarnings("unchecked")
    void publishesCounterpartyStatusWithItsOwnPartitionKeyAndFields() {
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        var objectMapper = JsonMapper.builder().build();
        var publisher = new CreditRequestKafkaPublisher(kafkaTemplate, objectMapper);
        NotificationOutbox event = new NotificationOutbox();
        event.setId(42L);
        event.setFinancialInstitutionId(5L);
        event.setEventType("COUNTERPARTY_STATUS_UPDATED");
        event.setCounterpartyId(123L);
        event.setStatus("ACTIVE");

        publisher.publish(event);

        var payload = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("credit-request-events"), eq("counterparty:123"), payload.capture());
        assertThat(objectMapper.readTree(payload.getValue())).isEqualTo(objectMapper.readTree("""
                {"id":42,"financialInstitutionId":5,"eventType":"COUNTERPARTY_STATUS_UPDATED",
                 "counterpartyId":123,"status":"ACTIVE"}
                """));
    }

    @Test
    @SuppressWarnings("unchecked")
    void publishesFacilityLimitUpdateWithItsOwnPartitionKeyAndExactAmounts() {
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        var objectMapper = JsonMapper.builder().build();
        var publisher = new CreditRequestKafkaPublisher(kafkaTemplate, objectMapper);
        NotificationOutbox event = new NotificationOutbox();
        event.setId(42L);
        event.setFinancialInstitutionId(5L);
        event.setEventType("CREDIT_LIMIT_UPDATED");
        event.setCreditLimitId(123L);
        event.setOldLimitAmount(new BigDecimal("50000.00"));
        event.setNewLimitAmount(new BigDecimal("9007199254740993.23"));

        publisher.publish(event);

        var payload = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("credit-request-events"), eq("credit-limit:123"), payload.capture());
        assertThat(payload.getValue()).contains("\"oldLimitAmount\":50000.00", "\"newLimitAmount\":9007199254740993.23");
        assertThat(objectMapper.readTree(payload.getValue()).propertyNames()).containsExactlyInAnyOrder(
                "id", "financialInstitutionId", "eventType", "creditLimitId", "oldLimitAmount", "newLimitAmount");
    }

    @Test
    @SuppressWarnings("unchecked")
    void publishesGroupLimitUpdateWithItsOwnPartitionKeyAndExactAmounts() {
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        var objectMapper = JsonMapper.builder().build();
        var publisher = new CreditRequestKafkaPublisher(kafkaTemplate, objectMapper);
        NotificationOutbox event = new NotificationOutbox();
        event.setId(42L);
        event.setFinancialInstitutionId(5L);
        event.setEventType("GROUP_CREDIT_LIMIT_UPDATED");
        event.setGroupCreditLimitId(123L);
        event.setCorporateGroupId(456L);
        event.setOldLimitAmount(new BigDecimal("100000.00"));
        event.setNewLimitAmount(new BigDecimal("9007199254740993.23"));

        publisher.publish(event);

        var payload = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("credit-request-events"), eq("group-credit-limit:123"), payload.capture());
        assertThat(payload.getValue()).contains("\"groupCreditLimitId\":123", "\"corporateGroupId\":456",
                "\"oldLimitAmount\":100000.00", "\"newLimitAmount\":9007199254740993.23");
        assertThat(objectMapper.readTree(payload.getValue()).propertyNames()).containsExactlyInAnyOrder(
                "id", "financialInstitutionId", "eventType", "groupCreditLimitId", "corporateGroupId", "oldLimitAmount", "newLimitAmount");
    }
}
