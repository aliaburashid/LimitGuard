package com.example.activityservice.repository;

import com.example.activityservice.dto.CreditRequestNotification;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "ACTIVITY_EMAIL_TEST_JDBC_URL", matches = ".+")
class CreditRequestEmailDeliveryRepositoryTest {

    private static JdbcTemplate jdbc;
    private static CreditRequestEmailDeliveryRepository deliveries;

    @BeforeAll
    static void prepareDisposableDatabase() {
        String url = System.getenv("ACTIVITY_EMAIL_TEST_JDBC_URL");
        if (!url.matches("jdbc:postgresql://127[.]0[.]0[.]1:[0-9]{1,5}/limitguard_email_test")
                || url.contains(":5432/")) {
            throw new IllegalArgumentException("Use a disposable loopback PostgreSQL database for this test");
        }
        var dataSource = new DriverManagerDataSource(url, "email_test", "");
        new ResourceDatabasePopulator(new ClassPathResource("credit-request-email-deliveries.sql")).execute(dataSource);
        jdbc = new JdbcTemplate(dataSource);
        deliveries = new CreditRequestEmailDeliveryRepository(jdbc);
    }

    @Test
    void redeliveryDoesNotOverwriteTheOriginalPendingRow() {
        CreditRequestNotification event = notification(4294967296L);
        deliveries.queueDelivery(event);
        event.setReason("different reason");
        deliveries.queueDelivery(event);

        assertThat(jdbc.queryForList("""
                SELECT delivery_status, reason FROM public.credit_request_email_deliveries WHERE event_id = ?
                """, event.getId())).singleElement().satisfies(row -> {
                    assertThat(row.get("delivery_status")).isEqualTo("PENDING");
                    assertThat(row.get("reason")).isEqualTo("test reason");
                });
    }

    @Test
    void concurrentConsumersQueueExactlyOnePendingRow() throws Exception {
        CreditRequestNotification event = notification(4294967297L);
        var ready = new CountDownLatch(2);
        var workers = Executors.newFixedThreadPool(2);
        try {
            var first = workers.submit(() -> queueTogether(ready, event));
            var second = workers.submit(() -> queueTogether(ready, event));
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        } finally {
            workers.shutdownNow();
        }

        assertThat(jdbc.queryForList("""
                SELECT delivery_status FROM public.credit_request_email_deliveries WHERE event_id = ?
                """, String.class, event.getId())).containsExactly("PENDING");
    }

    @Test
    void concurrentWorkersCanClaimTheSameEventOnlyOnce() throws Exception {
        CreditRequestNotification event = notification(4294967298L);
        deliveries.queueDelivery(event);
        var ready = new CountDownLatch(2);
        var workers = Executors.newFixedThreadPool(2);
        try {
            var first = workers.submit(() -> claimTogether(ready, event.getId()));
            var second = workers.submit(() -> claimTogether(ready, event.getId()));
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        } finally {
            workers.shutdownNow();
        }
        assertThat(deliveries.findPendingDeliveries()).noneMatch(row -> row.getEventId() == event.getId());

        deliveries.finishDelivery(event.getId(), "UNCERTAIN");
        deliveries.queueDelivery(event);
        assertThat(deliveries.claimDelivery(event.getId())).isFalse();
        assertThat(jdbc.queryForObject("""
                SELECT delivery_status FROM public.credit_request_email_deliveries WHERE event_id = ?
                """, String.class, event.getId())).isEqualTo("UNCERTAIN");
    }

    private boolean claimTogether(CountDownLatch ready, long eventId) throws InterruptedException {
        ready.countDown();
        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        return deliveries.claimDelivery(eventId);
    }

    private Void queueTogether(CountDownLatch ready, CreditRequestNotification event) throws InterruptedException {
        ready.countDown();
        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        deliveries.queueDelivery(event);
        return null;
    }

    private CreditRequestNotification notification(long id) {
        var event = new CreditRequestNotification();
        event.setId(id);
        event.setFinancialInstitutionId(5L);
        event.setCreditRequestId(125L);
        event.setRequesterId(15L);
        event.setEventType("CREDIT_REQUEST_REJECTED");
        event.setReason("test reason");
        return event;
    }
}
