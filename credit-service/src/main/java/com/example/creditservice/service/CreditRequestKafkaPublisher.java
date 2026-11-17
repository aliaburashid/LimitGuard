package com.example.creditservice.service;

import com.example.creditservice.dto.CounterpartyStatusNotification;
import com.example.creditservice.dto.CreditLimitNotification;
import com.example.creditservice.dto.CreditRequestNotification;
import com.example.creditservice.dto.GroupCreditLimitNotification;
import com.example.creditservice.model.NotificationOutbox;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.CompletableFuture;
import org.springframework.kafka.support.SendResult;

// This service sends credit request notification events to Kafka
@Service
@RequiredArgsConstructor
public class CreditRequestKafkaPublisher {

    // KafkaTemplate -> Spring Kafka class used to send messages to a kafka topic
    //  First String -> The message key.
    //  Second String -> The message content (JSON text).
    private final KafkaTemplate<String, String> kafkaTemplate;

    // Converts Java objects into JSON text
    private final ObjectMapper objectMapper;

    // The Kafka topic where credit request events will be sent
    // A topic is like a named channel where Kafka stores messages
    private static final String TOPIC = "credit-request-events";

    // NotificationOutbox event -> The notification we want to send
    // CompletableFuture -> Represents a result that will be available later
    // SendResult -> Contains information about the Kafka send result
    public CompletableFuture<SendResult<String, String>> publish(NotificationOutbox event) {

        if ("COUNTERPARTY_STATUS_UPDATED".equals(event.getEventType())) {
            // Keep updates for the same counterparty on the same Kafka partition.
            CounterpartyStatusNotification notification = new CounterpartyStatusNotification(
                    event.getId(), event.getFinancialInstitutionId(), event.getEventType(),
                    event.getCounterpartyId(), event.getStatus());
            return kafkaTemplate.send(TOPIC, "counterparty:" + event.getCounterpartyId(),
                    objectMapper.writeValueAsString(notification));
        }

        if ("CREDIT_LIMIT_UPDATED".equals(event.getEventType())) {
            // Use the facility ID to keep its limit updates on the same partition.
            CreditLimitNotification notification = new CreditLimitNotification(
                    event.getId(), event.getFinancialInstitutionId(), event.getEventType(),
                    event.getCreditLimitId(), event.getOldLimitAmount(), event.getNewLimitAmount());
            return kafkaTemplate.send(TOPIC, "credit-limit:" + event.getCreditLimitId(),
                    objectMapper.writeValueAsString(notification));
        }

        if ("GROUP_CREDIT_LIMIT_UPDATED".equals(event.getEventType())) {
            // Use the shared limit ID to keep its updates on the same Kafka partition.
            GroupCreditLimitNotification notification = new GroupCreditLimitNotification(
                    event.getId(), event.getFinancialInstitutionId(), event.getEventType(),
                    event.getGroupCreditLimitId(), event.getCorporateGroupId(),
                    event.getOldLimitAmount(), event.getNewLimitAmount());
            return kafkaTemplate.send(TOPIC, "group-credit-limit:" + event.getGroupCreditLimitId(),
                    objectMapper.writeValueAsString(notification));
        }

        // Copy only the credit request fields that Kafka consumers expect.
        CreditRequestNotification notification = new CreditRequestNotification(
                event.getId(),
                event.getFinancialInstitutionId(),
                event.getCreditRequestId(),
                event.getRequesterId(),
                event.getStatus(),
                event.getEventType(),
                event.getReason(),
                event.getCreatedAt(),
                event.getPublishedAt());

        // Convert the notification DTO into JSON text.
        String message = objectMapper.writeValueAsString(notification);

        // Use the credit request ID as the Kafka message key
        // For example, if credit request 25 generates three notifications, all three use the key "25".
        // This helps Kafka place them in the same partition, preserving their order within that partition
        String key = event.getCreditRequestId().toString();

        // Send the message to the Kafka topic
        // The returned future lets us check whether it succeeded
        return kafkaTemplate.send(TOPIC, key, message);
    }
}
