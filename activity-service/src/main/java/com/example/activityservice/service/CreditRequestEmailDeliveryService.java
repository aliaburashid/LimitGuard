package com.example.activityservice.service;

import com.example.activityservice.client.NotificationRecipientClient;
import com.example.activityservice.dto.CreditRequestEmailDelivery;
import com.example.activityservice.repository.CreditRequestEmailDeliveryRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.util.Optional;

@Service
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(name = "activity.email.delivery-enabled", havingValue = "true")
public class CreditRequestEmailDeliveryService {

    private static final Logger logger = LoggerFactory.getLogger(CreditRequestEmailDeliveryService.class);
    private final CreditRequestEmailDeliveryRepository deliveries;
    private final NotificationRecipientClient recipients;
    private final EmailService emails;

    @Scheduled(fixedDelay = 30_000)
    public void deliverPendingEmails() {
        for (CreditRequestEmailDelivery delivery : deliveries.findPendingDeliveries()) {
            deliver(delivery);
        }
    }

    private void deliver(CreditRequestEmailDelivery delivery) {
        long eventId = delivery.getEventId();

        // Save the claim before calling Auth or SMTP so only one worker can send.
        if (!deliveries.claimDelivery(eventId)) {
            return;
        }

        Optional<String> recipient;
        try {
            recipient = recipients.findRecipient(delivery.getRequesterId(), delivery.getFinancialInstitutionId());
        } catch (RestClientException exception) {
            // SMTP has not been called, so this event can safely retry later.
            deliveries.finishDelivery(eventId, "PENDING");
            logger.warn("Auth recipient lookup failed for email event {}", eventId);
            return;
        }

        if (recipient.isEmpty()) {
            deliveries.finishDelivery(eventId, "SKIPPED");
            return;
        }

        try {
            emails.sendCreditRequestEmail(recipient.get(), delivery);
        } catch (MailException exception) {
            // SMTP may have accepted the message before the connection failed.
            deliveries.finishDelivery(eventId, "UNCERTAIN");
            logger.warn("Email event {} requires delivery review", eventId);
            return;
        }

        // If this write fails, SENDING stays in the table for review instead of retrying.
        deliveries.finishDelivery(eventId, "SENT");
    }
}
