package com.example.activityservice.repository;

import com.example.activityservice.dto.CreditRequestEmailDelivery;
import com.example.activityservice.dto.CreditRequestNotification;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class CreditRequestEmailDeliveryRepository {

    private final JdbcTemplate jdbcTemplate;

    public void queueDelivery(CreditRequestNotification notification) {
        // PostgreSQL accepts only one row for an outbox event, even when consumers race.
        jdbcTemplate.update("""
                INSERT INTO public.credit_request_email_deliveries
                    (event_id, financial_institution_id, credit_request_id, requester_id, event_type, reason)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (event_id) DO NOTHING
                """,
                notification.getId(), notification.getFinancialInstitutionId(), notification.getCreditRequestId(),
                notification.getRequesterId(), notification.getEventType(), notification.getReason());
    }

    // Only pending records can be attempted automatically.
    public List<CreditRequestEmailDelivery> findPendingDeliveries() {
        return jdbcTemplate.query("""
                SELECT event_id, financial_institution_id, credit_request_id, requester_id, event_type, reason
                FROM public.credit_request_email_deliveries
                WHERE delivery_status = 'PENDING'
                ORDER BY queued_at, event_id
                LIMIT 25
                """, (row, index) -> new CreditRequestEmailDelivery(
                        row.getLong("event_id"), row.getLong("financial_institution_id"),
                        row.getLong("credit_request_id"), row.getLong("requester_id"),
                        row.getString("event_type"), row.getString("reason")));
    }

    // PostgreSQL rechecks the status after a competing worker releases its row lock.
    public boolean claimDelivery(long eventId) {
        return jdbcTemplate.update("""
                UPDATE public.credit_request_email_deliveries
                SET delivery_status = 'SENDING'
                WHERE event_id = ? AND delivery_status = 'PENDING'
                """, eventId) == 1;
    }

    public void finishDelivery(long eventId, String status) {
        jdbcTemplate.update("""
                UPDATE public.credit_request_email_deliveries
                SET delivery_status = ?
                WHERE event_id = ? AND delivery_status = 'SENDING'
                """, status, eventId);
    }
}
