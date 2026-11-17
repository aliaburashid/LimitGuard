package com.example.creditservice.repository;

import com.example.creditservice.model.NotificationOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

// lets us save notification events using Spring Data JPA:
// For example, when a Risk Officer approves a credit request,
// Credit Service will save an event containing the request ID, institution ID, requester ID and new status.
// Later, a Kafka publisher will read that event and forward it to Notification Service.
@Repository
public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, Long> {

    // Finds up to 50 unpublished events, starting with the oldest notification events that have not been
    // successfully published to Kafka yet.
    // An event is unpublished when publishedAt is null.
    // We limit the results to avoid loading too many events at once.
    List<NotificationOutbox> findTop50ByPublishedAtIsNullOrderByCreatedAtAsc();

}
