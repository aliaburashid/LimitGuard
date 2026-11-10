package com.example.limitguard.repository;

import com.example.limitguard.dto.CreditExposureSnapshotResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class CreditExposureSnapshotRepository {

    private final JdbcTemplate jdbc;

    // takes the lock in a separate statement so a waiting instance sees the winner's commit
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public int capture(boolean baseline) {
        jdbc.execute("SELECT pg_advisory_xact_lock(724211, 1)");

        // all facilities use the same committed view and the same database capture time
        return jdbc.update("""
                WITH capture AS (
                    SELECT statement_timestamp() AS captured_at,
                           (statement_timestamp() AT TIME ZONE 'UTC')::date AS reporting_date
                )
                INSERT INTO credit_exposure_snapshots
                    (institution_id, facility_id, captured_at, reporting_date, limit_amount, used_amount, reserved_amount)
                SELECT facility.financial_institution_id, facility.id, capture.captured_at, capture.reporting_date,
                       facility.limit_amount, facility.used_amount, facility.reserved_amount
                FROM credit_limits facility
                CROSS JOIN capture
                WHERE NOT EXISTS (
                    SELECT 1 FROM credit_exposure_snapshots history
                    WHERE history.institution_id = facility.financial_institution_id
                      AND (? OR history.reporting_date = capture.reporting_date)
                )
                ON CONFLICT (institution_id, facility_id, reporting_date) DO NOTHING
                """, baseline);
    }

    // matches the RM request register's institution and requester scope without counting repeat requests
    @Transactional(readOnly = true)
    public List<CreditExposureSnapshotResponse> findHistory(Long institutionId, Long requesterId) {
        return findHistory(institutionId, requesterId, false);
    }

    // Reads history and one current balance from the same committed database view.
    @Transactional(readOnly = true)
    public List<CreditExposureSnapshotResponse> findHistory(Long institutionId, Long requesterId, boolean includeCurrent) {
        return jdbc.query("""
                WITH authorized_facilities AS (
                    SELECT facility.*
                    FROM credit_limits facility
                    WHERE facility.financial_institution_id = ?
                      AND EXISTS (
                          SELECT 1 FROM credit_requests request
                          WHERE request.credit_limit_id = facility.id
                            AND request.requester_id = ?
                      )
                )
                SELECT snapshot.captured_at,
                       SUM(snapshot.limit_amount) AS limit_amount,
                       SUM(snapshot.used_amount) AS used_amount,
                       SUM(snapshot.reserved_amount) AS reserved_amount,
                       BOOL_OR(snapshot.captured_at < (facility.created_at AT TIME ZONE 'Asia/Bahrain')
                           AND EXISTS (
                               SELECT 1 FROM credit_requests demo
                               WHERE demo.credit_limit_id = facility.id
                                 AND demo.seed_key LIKE 'citi-panel-v1-%'
                           )) AS synthetic,
                       FALSE AS is_current
                FROM credit_exposure_snapshots snapshot
                JOIN authorized_facilities facility ON facility.id = snapshot.facility_id
                    AND facility.financial_institution_id = snapshot.institution_id
                GROUP BY snapshot.reporting_date, snapshot.captured_at

                UNION ALL

                SELECT statement_timestamp(), SUM(limit_amount), SUM(used_amount), SUM(reserved_amount), FALSE, TRUE
                FROM authorized_facilities
                WHERE ?
                HAVING COUNT(*) > 0
                ORDER BY is_current, captured_at
                """, (row, index) -> new CreditExposureSnapshotResponse(
                row.getObject("captured_at", OffsetDateTime.class).toInstant(),
                row.getBigDecimal("limit_amount"),
                row.getBigDecimal("used_amount"),
                row.getBigDecimal("reserved_amount"),
                row.getBoolean("synthetic"),
                row.getBoolean("is_current")
        ), institutionId, requesterId, includeCurrent);
    }
}
