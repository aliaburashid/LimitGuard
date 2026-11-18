package com.example.activityservice.service;

import com.example.activityservice.dto.CreditRequestNotification;
import com.example.activityservice.repository.CreditRequestEmailDeliveryRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class CreditRequestEmailConsumer {

    private static final Logger logger = LoggerFactory.getLogger(CreditRequestEmailConsumer.class);
    private static final Map<String, String> EMAIL_EVENT_STATUSES = Map.of(
            "CREDIT_REQUEST_APPROVED", "RESERVED",
            "CREDIT_REQUEST_REJECTED", "REJECTED",
            "CREDIT_REQUEST_EXPIRED", "EXPIRED");

    private final ObjectMapper objectMapper;
    private final CreditRequestEmailDeliveryRepository deliveries;

    // A separate group lets email track its own progress through the same topic.
    @KafkaListener(topics = "credit-request-events",
            groupId = "${activity.email.group-id}",
            autoStartup = "${activity.email.enabled}",
            containerFactory = "emailKafkaListenerContainerFactory",
            properties = "auto.offset.reset=latest")
    public void receiveNotification(@Payload(required = false) String message) {
        if (message == null) {
            logger.warn("Skipping malformed credit request email notification");
            return;
        }

        CreditRequestNotification notification;
        try {
            JsonNode json = objectMapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(message);
            if (json == null || !json.isObject()) {
                logger.warn("Skipping malformed credit request email notification");
                return;
            }

            String expectedStatus = EMAIL_EVENT_STATUSES.get(json.path("eventType").asText());
            if (expectedStatus == null) {
                return;
            }

            // Check the event can be tied to one requester and institution before queuing it.
            if (!expectedStatus.equals(json.path("status").asText())
                    || !isPositiveId(json.path("id")) || !isPositiveId(json.path("financialInstitutionId"))
                    || !isPositiveId(json.path("creditRequestId")) || !isPositiveId(json.path("requesterId"))
                    || (json.hasNonNull("reason") && !json.path("reason").isTextual())) {
                logger.warn("Skipping malformed credit request email notification");
                return;
            }

            notification = objectMapper.treeToValue(json, CreditRequestNotification.class);
            if (notification.getCreatedAt() == null) {
                logger.warn("Skipping malformed credit request email notification");
                return;
            }
        } catch (JacksonException exception) {
            // Skip invalid JSON without logging the message contents.
            logger.warn("Skipping malformed credit request email notification");
            return;
        }

        // Let database failures reach Kafka's email-only retry handler.
        deliveries.queueDelivery(notification);
    }

    private boolean isPositiveId(JsonNode value) {
        return value.isIntegralNumber() && value.canConvertToLong() && value.asLong() > 0;
    }
}
