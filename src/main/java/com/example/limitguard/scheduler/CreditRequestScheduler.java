package com.example.limitguard.scheduler;

import com.example.limitguard.model.CreditRequest;
import com.example.limitguard.enums.CreditRequestStatus;
import com.example.limitguard.repository.CreditRequestRepository;
import com.example.limitguard.service.CreditRequestService;
import lombok.AllArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

// It tells Spring: Create and manage an object of this class for me
// that allows Spring to automatically run its scheduled method
@Component
@AllArgsConstructor
public class CreditRequestScheduler {

    // repository is used to find reservations from the database
    private final CreditRequestRepository creditRequestRepository;

    // service is used to expire each reservation
    private final CreditRequestService creditRequestService;

    // Spring automatically runs this method every 1 minute
    // 60,000 milliseconds = 1 minute
    // does NOT mean reservations expire after one minute the (Reservation lifetime = 24 hours)
    // this means how often LimitGuard checks = every 1 minute
    @Scheduled(fixedRate = 60000)
    public void expireUnusedReservations() {
        // finds all RESERVED requests where expiresAt
        // is earlier than the current time
        List<CreditRequest> expiredReservations =
                creditRequestRepository.findByStatusAndExpiresAtBefore(
                        CreditRequestStatus.RESERVED,
                        LocalDateTime.now()
                );

        // goes through every expired reservation that was found
        for (CreditRequest creditRequest : expiredReservations) {

            // calls the service method which changes
            // the request from RESERVED to EXPIRED
            creditRequestService.expireCreditRequest(creditRequest.getId());
        }
    }
}