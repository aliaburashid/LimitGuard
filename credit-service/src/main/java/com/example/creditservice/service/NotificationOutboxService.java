package com.example.creditservice.service;

import com.example.creditservice.enums.CounterpartyStatus;
import com.example.creditservice.enums.CreditRequestStatus;
import com.example.creditservice.model.NotificationOutbox;
import com.example.creditservice.repository.NotificationOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

// Handles saving notification events to PostgreSQL.
// These events will later be published to Kafka.
@Service

// Lombok automatically generates a constructor for all final fields.
// Spring uses that constructor to inject the required dependencies.
@RequiredArgsConstructor
public class NotificationOutboxService {

    // Spring injects this repository through the Lombok-generated constructor.
    private final NotificationOutboxRepository notificationOutboxRepository;

    // Records a notification event when a credit request changes status.
    // This method must run inside the existing credit request transaction.
    public void recordCreditRequestEvent(
            Long institutionId,
            Long creditRequestId,
            Long requesterId,
            CreditRequestStatus status,
            String eventType,
            String reason) {

        // Create a new notification event.
        NotificationOutbox event = new NotificationOutbox();

        // Identify the bank that owns this notification.
        event.setFinancialInstitutionId(institutionId);

        // Identify the credit request that triggered the event.
        event.setCreditRequestId(creditRequestId);

        // Identify the user who submitted the credit request.
        event.setRequesterId(requesterId);

        // Convert the enum status to a String.
        event.setStatus(status.name());

        // Describe the event, such as CREDIT_REQUEST_APPROVED.
        event.setEventType(eventType);

        // Store an optional reason, such as a rejection explanation.
        event.setReason(reason);

        // Save the event in PostgreSQL.
        // When called within the credit request transaction,
        // both the credit update and event are committed together.
        notificationOutboxRepository.save(event);
    }
    // Queue the status update for the institution of the user who made the change.
    public void recordCounterpartyStatusEvent(Long institutionId, Long counterpartyId, CounterpartyStatus status) {
        NotificationOutbox event = new NotificationOutbox();
        event.setFinancialInstitutionId(institutionId);
        event.setCounterpartyId(counterpartyId);
        event.setStatus(status.name());
        event.setEventType("COUNTERPARTY_STATUS_UPDATED");
        notificationOutboxRepository.save(event);
    }

    // Queue the old and new approved amounts for the institution that owns the facility.
    public void recordCreditLimitUpdatedEvent(
            Long institutionId, Long creditLimitId, BigDecimal oldLimitAmount, BigDecimal newLimitAmount) {
        NotificationOutbox event = new NotificationOutbox();
        event.setFinancialInstitutionId(institutionId);
        event.setCreditLimitId(creditLimitId);
        event.setOldLimitAmount(oldLimitAmount);
        event.setNewLimitAmount(newLimitAmount);
        event.setEventType("CREDIT_LIMIT_UPDATED");
        notificationOutboxRepository.save(event);
    }

    // Queue the shared limit amounts for the institution that owns the limit.
    public void recordGroupCreditLimitUpdatedEvent(Long institutionId, Long groupCreditLimitId,
            Long corporateGroupId, BigDecimal oldLimitAmount, BigDecimal newLimitAmount) {
        NotificationOutbox event = new NotificationOutbox();
        event.setFinancialInstitutionId(institutionId);
        event.setGroupCreditLimitId(groupCreditLimitId);
        event.setCorporateGroupId(corporateGroupId);
        event.setOldLimitAmount(oldLimitAmount);
        event.setNewLimitAmount(newLimitAmount);
        event.setEventType("GROUP_CREDIT_LIMIT_UPDATED");
        notificationOutboxRepository.save(event);
    }
}
