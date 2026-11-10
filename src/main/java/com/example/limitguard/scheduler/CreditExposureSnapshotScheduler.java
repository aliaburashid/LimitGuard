package com.example.limitguard.scheduler;

import com.example.limitguard.repository.CreditExposureSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "limitguard.exposure-snapshots.enabled", havingValue = "true")
public class CreditExposureSnapshotScheduler {

    private static final Logger logger = LoggerFactory.getLogger(CreditExposureSnapshotScheduler.class);

    private final CreditExposureSnapshotRepository snapshots;

    // captures opening balances on first startup without changing the date of any existing history
    @EventListener(ApplicationReadyEvent.class)
    public void captureBaseline() {
        try {
            logCapture(snapshots.capture(true));
        } catch (RuntimeException exception) {
            // the scheduled capture can retry without preventing the credit workspace from starting
            logger.error("Unable to record baseline credit exposure", exception);
        }
    }

    // starts at midnight UTC and retries hourly on the same day if the first capture did not commit
    @Async("creditExposureSnapshotExecutor")
    @Scheduled(cron = "0 0 * * * *", zone = "UTC")
    public void captureDaily() {
        logCapture(snapshots.capture(false));
    }

    private void logCapture(int facilities) {
        if (facilities > 0) {
            logger.info("Recorded daily credit exposure for {} facilities", facilities);
        }
    }
}
