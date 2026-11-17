package com.example.creditservice.scheduler;

import com.example.creditservice.model.NotificationOutbox;
import com.example.creditservice.repository.NotificationOutboxRepository;
import com.example.creditservice.service.CreditRequestKafkaPublisher;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutionException;

// CLASS responsible for finding those saved notifications and sending them to Kafka.
@Component
@RequiredArgsConstructor
// We don't want the relay to start publishing notifications before Kafka is ready.
// when limitguard.notification-relay.enabled" is false -> The relay component isn't created, so it won't run.
@ConditionalOnProperty(name = "limitguard.notification-relay.enabled", havingValue = "true")
public class NotificationOutboxRelay {

    private static final Logger logger = LoggerFactory.getLogger(NotificationOutboxRelay.class);

    // Communicates with PostgreSQL through Spring Data JPA.
    // It retrieves notification records that haven't been published.
    private final NotificationOutboxRepository notificationOutboxRepository;
    // Calls the service responsible for sending events to Kafka.
    private final CreditRequestKafkaPublisher kafkaPublisher;
    // Allows us to execute SQL directly.
    // In this class, it's used only to acquire a PostgreSQL lock.
    private final JdbcTemplate jdbcTemplate;

    // This tells Spring to execute the method repeatedly.
    // fixedDelay waits five seconds after the previous execution finishes before starting the next one.
    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void publishPendingNotifications() {

        // prevents two Credit Service instances from sending the same notifications at the same time.
        // Try to lock notification publishing so only one Credit Service can do it.
        Boolean acquiredLock = jdbcTemplate.queryForObject(
                "SELECT pg_try_advisory_xact_lock(724211, 2)", Boolean.class);

        // If another Credit Service already has the lock, stop.
        if (!Boolean.TRUE.equals(acquiredLock)) {
            return;
        }

        // Find up to 50 unpublished notifications, starting with the oldest.
        for (NotificationOutbox event : notificationOutboxRepository.findTop50ByPublishedAtIsNullOrderByCreatedAtAsc()) {

            try {
                // Send the notification to Kafka and wait for confirmation.
                kafkaPublisher.publish(event).get();

            } catch (InterruptedException exception) {
                // If the thread is interrupted, stop publishing.
                Thread.currentThread().interrupt();

                // Log which notification was interrupted.
                logger.warn("Notification publishing interrupted for event {}", event.getId());
                return;

            } catch (ExecutionException | RuntimeException exception) {
                // If Kafka fails, leave the notification unpublished so we can retry later.
                logger.warn("Notification event {} remains unpublished", event.getId(), exception);
                return;
            }

            // Kafka confirmed the message, so record when it was published.
            event.setPublishedAt(LocalDateTime.now());
        }
    }
}
