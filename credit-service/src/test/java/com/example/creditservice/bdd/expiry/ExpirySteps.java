package com.example.creditservice.bdd.expiry;

import com.example.creditservice.enums.CreditRequestStatus;
import com.example.creditservice.model.Counterparty;
import com.example.creditservice.model.CreditLimit;
import com.example.creditservice.model.CreditRequest;
import com.example.creditservice.repository.CounterpartyRepository;
import com.example.creditservice.repository.CreditLimitRepository;
import com.example.creditservice.repository.CreditRequestRepository;
import com.example.creditservice.repository.NotificationOutboxRepository;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

public class ExpirySteps {

    private final CounterpartyRepository counterparties;
    private final CreditLimitRepository limits;
    private final CreditRequestRepository requests;
    private final NotificationOutboxRepository outbox;
    private final TransactionTemplate transactions;

    private CreditLimit facility;
    private CreditRequest overdue;
    private CreditRequest future;

    public ExpirySteps(CounterpartyRepository counterparties, CreditLimitRepository limits,
                       CreditRequestRepository requests, NotificationOutboxRepository outbox,
                       TransactionTemplate transactions) {
        this.counterparties = counterparties;
        this.limits = limits;
        this.requests = requests;
        this.outbox = outbox;
        this.transactions = transactions;
    }

    @Given("a facility with an overdue and a future reservation")
    public void createReservations() {
        transactions.executeWithoutResult(transaction -> {
            Counterparty counterparty = new Counterparty();
            counterparty.setName("Automatic expiry test counterparty");

            facility = new CreditLimit();
            facility.setCounterparty(counterparties.save(counterparty));
            facility.setFinancialInstitutionId(5L);
            facility.setLimitAmount(new BigDecimal("1000.00"));
            facility.setUsedAmount(new BigDecimal("100.00"));
            facility.setReservedAmount(new BigDecimal("400.00"));
            facility = limits.save(facility);

            LocalDateTime now = LocalDateTime.now();
            overdue = reserve("125.00", now.minusMinutes(1));
            future = reserve("275.00", now.plusHours(1));
        });
    }

    @When("the expiry scheduler runs")
    public void waitForScheduledExpiry() throws InterruptedException {
        // The scheduler may have scanned before the fixture transaction committed.
        long deadline = System.nanoTime() + Duration.ofSeconds(75).toNanos();
        CreditRequestStatus status = statusOf(overdue);
        while (status == CreditRequestStatus.RESERVED && System.nanoTime() < deadline) {
            Thread.sleep(250);
            status = statusOf(overdue);
        }
        assertThat(status).as("status after waiting for the scheduled scan")
                .isEqualTo(CreditRequestStatus.EXPIRED);
    }

    @Then("only the overdue reservation is expired")
    public void verifyStatuses() {
        assertThat(statusOf(overdue)).isEqualTo(CreditRequestStatus.EXPIRED);
        assertThat(statusOf(future)).isEqualTo(CreditRequestStatus.RESERVED);
    }

    @Then("the overdue amount is released back to facility headroom")
    public void verifyHeadroom() {
        CreditLimit updated = limits.findById(facility.getId()).orElseThrow();
        assertThat(updated.getUsedAmount()).isEqualByComparingTo("100.00");
        assertThat(updated.getReservedAmount()).isEqualByComparingTo("275.00");
        assertThat(updated.getLimitAmount().subtract(updated.getUsedAmount()).subtract(updated.getReservedAmount()))
                .isEqualByComparingTo("625.00");
    }

    @Then("exactly one expiry notification is queued for the overdue reservation")
    public void verifyExpiryEvent() {
        assertThat(outbox.findAll()).singleElement().satisfies(event -> {
            assertThat(event.getEventType()).isEqualTo("CREDIT_REQUEST_EXPIRED");
            assertThat(event.getCreditRequestId()).isEqualTo(overdue.getId());
            assertThat(event.getRequesterId()).isEqualTo(overdue.getRequesterId());
            assertThat(event.getFinancialInstitutionId()).isEqualTo(facility.getFinancialInstitutionId());
            assertThat(event.getStatus()).isEqualTo("EXPIRED");
            assertThat(event.getPublishedAt()).isNull();
        });
    }

    private CreditRequest reserve(String amount, LocalDateTime expiresAt) {
        CreditRequest request = new CreditRequest();
        request.setCreditLimit(facility);
        request.setRequesterId(7L);
        request.setAmount(new BigDecimal(amount));
        request.setStatus(CreditRequestStatus.RESERVED);
        request.setExpiresAt(expiresAt);
        return requests.save(request);
    }

    private CreditRequestStatus statusOf(CreditRequest request) {
        return requests.findById(request.getId()).orElseThrow().getStatus();
    }
}
