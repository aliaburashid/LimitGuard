package com.example.activityservice.service;

import com.example.activityservice.dto.CounterpartyStatusNotification;
import com.example.activityservice.dto.CreditLimitNotification;
import com.example.activityservice.dto.CreditRequestNotification;
import com.example.activityservice.dto.GroupCreditLimitNotification;
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

import java.util.Set;

// Listens for credit request notifications sent by Credit Service through Kafka.
@Service
@RequiredArgsConstructor
public class CreditRequestNotificationConsumer {

    // Used to print information and warnings in the application logs
    private static final Logger logger =
            LoggerFactory.getLogger(CreditRequestNotificationConsumer.class);

    // Converts JSON messages received from Kafka into Java objects
    private final ObjectMapper objectMapper;
    private static final Set<String> COUNTERPARTY_STATUSES = Set.of("ACTIVE", "FROZEN", "CLOSED");

    // Sends credit request updates to connected frontend users through SSE
    private final CreditRequestEventService creditRequestEventService;

    // Listens to the Kafka topic called "credit-request-events"
    // Kafka automatically calls this method when a message is received
    @KafkaListener(topics = "credit-request-events")
    public void receiveNotification(@Payload(required = false) String message) {

        // Check whether Kafka delivered a null message
        if (message == null) {
            logger.warn("Skipping malformed credit request notification");
            return; // Stop processing this message
        }

        CreditRequestNotification notification;

        try {
            // Convert the JSON message into a CreditRequestNotification DTO
            // Read the event type before choosing which notification DTO to use.
            JsonNode json = objectMapper.reader()
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readTree(message);
            if (json == null || !json.isObject()) {
                logger.warn("Skipping malformed credit request notification");
                return;
            }
            if ("COUNTERPARTY_STATUS_UPDATED".equals(json.path("eventType").asText())) {
                receiveCounterpartyStatus(json);
                return;
            }
            if ("CREDIT_LIMIT_UPDATED".equals(json.path("eventType").asText())) {
                receiveCreditLimitUpdate(json, message);
                return;
            }
            if ("GROUP_CREDIT_LIMIT_UPDATED".equals(json.path("eventType").asText())) {
                receiveGroupCreditLimitUpdate(json, message);
                return;
            }
            notification = objectMapper.treeToValue(json, CreditRequestNotification.class);

        } catch (JacksonException exception) {

            // If JSON conversion fails, log a warning and skip the message
            // Returning normally lets Kafka finish processing this record
            logger.warn("Skipping malformed credit request notification");
            return;
        }

        // Check that the notification contains all required information
        // If any required attribute is missing or invalid, skip the message
        if (notification == null || notification.getId() == null
                || notification.getCreditRequestId() == null || notification.getFinancialInstitutionId() == null
                || notification.getRequesterId() == null || notification.getCreatedAt() == null
                || notification.getStatus() == null || notification.getStatus().isBlank()
                || notification.getEventType() == null
                || !notification.getEventType().matches("CREDIT_REQUEST_[A-Z_]+")) {

            logger.warn("Skipping malformed credit request notification");
            return;
        }

        // Log the notification IDs and event type for debugging
        logger.info("Received credit request notification. Event ID={}, credit request ID={}, event type={}",
                notification.getId(), notification.getCreditRequestId(), notification.getEventType());

        // Pass the notification to CreditRequestEventService
        // It sends the status update to connected browsers from the same institution
        creditRequestEventService.publishStatusUpdate(
                notification.getFinancialInstitutionId(),
                notification.getCreditRequestId(),
                notification.getStatus());
    }

    // Check the counterparty message before sending it to any connected browser.
    private void receiveCounterpartyStatus(JsonNode json) {
        if (!isPositiveId(json.path("id")) || !isPositiveId(json.path("financialInstitutionId"))
                || !isPositiveId(json.path("counterpartyId"))
                || !COUNTERPARTY_STATUSES.contains(json.path("status").asText())) {
            logger.warn("Skipping malformed counterparty status notification");
            return;
        }

        CounterpartyStatusNotification notification = objectMapper.treeToValue(
                json, CounterpartyStatusNotification.class);
        creditRequestEventService.publishCounterpartyStatusUpdate(
                notification.getFinancialInstitutionId(), notification.getCounterpartyId(), notification.getStatus());
    }

    // Check the required fields before sending the update to connected browsers.
    private void receiveCreditLimitUpdate(JsonNode json, String message) {
        if (!isPositiveId(json.path("id")) || !isPositiveId(json.path("financialInstitutionId"))
                || !isPositiveId(json.path("creditLimitId"))
                || !json.path("oldLimitAmount").isNumber() || !json.path("newLimitAmount").isNumber()) {
            logger.warn("Skipping malformed credit limit notification");
            return;
        }

        // Read amounts directly as BigDecimal to preserve their precision and scale.
        CreditLimitNotification notification = objectMapper.readValue(message, CreditLimitNotification.class);
        creditRequestEventService.publishCreditLimitUpdate(
                notification.getFinancialInstitutionId(), notification.getCreditLimitId(),
                notification.getOldLimitAmount(), notification.getNewLimitAmount());
    }

    // Check both group IDs and the amounts before forwarding the notification.
    private void receiveGroupCreditLimitUpdate(JsonNode json, String message) {
        if (!isPositiveId(json.path("id")) || !isPositiveId(json.path("financialInstitutionId"))
                || !isPositiveId(json.path("groupCreditLimitId")) || !isPositiveId(json.path("corporateGroupId"))
                || !json.path("oldLimitAmount").isNumber() || !json.path("newLimitAmount").isNumber()) {
            logger.warn("Skipping malformed group credit limit notification");
            return;
        }

        // Read the original JSON as BigDecimal so the amounts keep their precision.
        GroupCreditLimitNotification notification = objectMapper.readValue(message, GroupCreditLimitNotification.class);
        creditRequestEventService.publishGroupCreditLimitUpdate(
                notification.getFinancialInstitutionId(), notification.getGroupCreditLimitId(),
                notification.getCorporateGroupId(), notification.getOldLimitAmount(), notification.getNewLimitAmount());
    }

    // Kafka IDs must be positive whole numbers that fit in a Java Long.
    private boolean isPositiveId(JsonNode value) {
        return value.isIntegralNumber() && value.canConvertToLong() && value.asLong() > 0;
    }
}
