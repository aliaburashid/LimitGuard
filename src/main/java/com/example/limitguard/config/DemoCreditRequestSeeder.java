
package com.example.limitguard.config;

import com.example.limitguard.enums.ApprovalDecisionType;
import com.example.limitguard.enums.CounterpartyStatus;
import com.example.limitguard.enums.CreditRequestStatus;
import com.example.limitguard.enums.UserRole;
import com.example.limitguard.model.ApprovalDecision;
import com.example.limitguard.model.CreditLimit;
import com.example.limitguard.model.CreditRequest;
import com.example.limitguard.model.User;
import com.example.limitguard.repository.ApprovalDecisionRepository;
import com.example.limitguard.repository.CreditLimitRepository;
import com.example.limitguard.repository.CreditRequestRepository;
import com.example.limitguard.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Component
@Profile("dev")
public class DemoCreditRequestSeeder {

    private final CreditLimitRepository creditLimitRepository;
    private final CreditRequestRepository creditRequestRepository;
    private final ApprovalDecisionRepository approvalDecisionRepository;
    private final UserRepository userRepository;
    private final EntityManager entityManager;

    public DemoCreditRequestSeeder(
            CreditLimitRepository creditLimitRepository,
            CreditRequestRepository creditRequestRepository,
            ApprovalDecisionRepository approvalDecisionRepository,
            UserRepository userRepository,
            EntityManager entityManager) {

        this.creditLimitRepository = creditLimitRepository;
        this.creditRequestRepository = creditRequestRepository;
        this.approvalDecisionRepository = approvalDecisionRepository;
        this.userRepository = userRepository;
        this.entityManager = entityManager;
    }

    // runs after the application starts
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seedCreditRequests() {

        // stores the banks used in the demo
        String[] banks = {
                "Citi",
                "Pearl Gulf Commercial Bank",
                "Al Noor Investment Bank",
                "Bahrain Meridian Bank",
                "Khaleej Capital Bank",
                "Gulf Horizon Financial Group",
                "Crescent International Bank"
        };

        // creates demo requests for every bank
        for (String bank : banks) {
            seedBankRequests(bank);
        }
    }

    private void seedBankRequests(String bankName) {

        // gets the credit limits belonging to this bank
        List<CreditLimit> limits = creditLimitRepository.findAll().stream()
                .filter(limit -> bankName.equals(
                        limit.getFinancialInstitution().getName()))
                .sorted(Comparator.comparing(
                        limit -> limit.getCounterparty().getName()))
                .toList();

        // gets employees belonging to this bank
        List<User> bankUsers = userRepository.findAll().stream()
                .filter(user -> user.getFinancialInstitution() != null)
                .filter(user -> bankName.equals(
                        user.getFinancialInstitution().getName()))
                .toList();

        // gets relationship managers who can submit requests
        List<User> managers = bankUsers.stream()
                .filter(user -> user.getRole() == UserRole.RELATIONSHIP_MANAGER)
                .sorted(Comparator.comparing(User::getEmail))
                .toList();

        // gets risk officers who can approve or reject requests
        List<User> riskOfficers = bankUsers.stream()
                .filter(user -> user.getRole() == UserRole.RISK_OFFICER)
                .sorted(Comparator.comparing(User::getEmail))
                .toList();

        // skips banks without the required employees or limits
        if (limits.isEmpty() || managers.isEmpty() || riskOfficers.isEmpty()) {
            System.out.println("Demo requests skipped for: " + bankName);
            return;
        }

        // chooses the employees for the demo
        User manager = managers.get(0);
        User riskOfficer = riskOfficers.get(0);

        // uses the correct presentation accounts for citi
        if (bankName.equals("Citi")) {
            manager = userRepository.findByEmail(
                    "aliaburashid@hotmail.com").orElse(null);

            riskOfficer = userRepository.findByEmail(
                    "burashid993@gmail.com").orElse(null);
        }

        // checks that both employees have the correct roles and bank
        if (manager == null || riskOfficer == null
                || manager.getFinancialInstitution() == null
                || riskOfficer.getFinancialInstitution() == null
                || !bankName.equals(manager.getFinancialInstitution().getName())
                || !bankName.equals(riskOfficer.getFinancialInstitution().getName())
                || manager.getRole() != UserRole.RELATIONSHIP_MANAGER
                || riskOfficer.getRole() != UserRole.RISK_OFFICER
                || manager.getId().equals(riskOfficer.getId())) {

            System.out.println("Demo employees not valid for: " + bankName);
            return;
        }

        // stores all the request statuses
        CreditRequestStatus[] statuses = {
                CreditRequestStatus.RESERVED,
                CreditRequestStatus.USED,
                CreditRequestStatus.PENDING_APPROVAL,
                CreditRequestStatus.CANCELLED,
                CreditRequestStatus.REJECTED,
                CreditRequestStatus.EXPIRED
        };

        // uses different dates for the request history
        LocalDateTime now = LocalDateTime.now();

        int created = 0;

        // creates three requests for each credit limit
        for (int round = 0; round < 3; round++) {

            for (int index = 0; index < limits.size(); index++) {

                CreditLimit limit = limits.get(index);

                // gives every demo request a unique key
                String seedKey = "demo-" +
                        limit.getFinancialInstitution().getId() + "-" +
                        limit.getId() + "-" + round;

                // skips requests already created on an earlier run
                if (creditRequestRepository.existsBySeedKey(seedKey)) {
                    continue;
                }

                // locks the limit before checking its balance
                entityManager.refresh(limit, LockModeType.PESSIMISTIC_WRITE);

                // checks again after locking to avoid duplicates
                if (creditRequestRepository.existsBySeedKey(seedKey)) {
                    continue;
                }

                // chooses a status based on the limit and round
                CreditRequestStatus status =
                        statuses[(index + round * 2) % statuses.length];

                // checks the current company status
                CounterpartyStatus counterpartyStatus =
                        limit.getCounterparty().getStatus();

                // frozen and closed companies only receive past requests
                if (counterpartyStatus != CounterpartyStatus.ACTIVE) {
                    status = (index + round) % 2 == 0
                            ? CreditRequestStatus.CANCELLED
                            : CreditRequestStatus.REJECTED;
                }

                // chooses a different amount for each request
                BigDecimal amount = BigDecimal.valueOf(
                        15000L + (index % 8) * 10000L + round * 5000L);

                // checks how much credit is still available
                BigDecimal headroom = limit.getLimitAmount()
                        .subtract(limit.getUsedAmount())
                        .subtract(limit.getReservedAmount());

                // skips allocations that exceed available headroom
                if ((status == CreditRequestStatus.RESERVED
                        || status == CreditRequestStatus.USED
                        || status == CreditRequestStatus.PENDING_APPROVAL)
                        && headroom.compareTo(amount) < 0) {
                    continue;
                }

                // creates dates spread across the past few months
                LocalDateTime submittedAt = now.minusDays(
                        5L + index * 3L + round * 12L);

                // creates the request
                CreditRequest request = new CreditRequest();
                request.setSeedKey(seedKey);
                request.setCreditLimit(limit);
                request.setRequester(manager);
                request.setAmount(amount);
                request.setStatus(status);

                // gives reserved requests a future expiry date
                if (status == CreditRequestStatus.RESERVED) {
                    request.setExpiresAt(now.plusDays(30));
                }

                // gives expired requests an old expiry date
                if (status == CreditRequestStatus.EXPIRED) {
                    request.setExpiresAt(submittedAt.plusHours(24));
                }

                // saves the request to the database
                creditRequestRepository.saveAndFlush(request);

                // sets historical creation and update dates
                entityManager.createNativeQuery("""
                        update credit_requests
                        set created_at = :createdAt,
                            updated_at = :updatedAt
                        where id = :requestId
                        """)
                        .setParameter("createdAt", submittedAt)
                        .setParameter("updatedAt", submittedAt.plusHours(3))
                        .setParameter("requestId", request.getId())
                        .executeUpdate();

                // increases reserved exposure for reserved requests
                if (status == CreditRequestStatus.RESERVED) {
                    limit.setReservedAmount(
                            limit.getReservedAmount().add(amount));
                    creditLimitRepository.save(limit);
                }

                // increases used exposure for used requests
                if (status == CreditRequestStatus.USED) {
                    limit.setUsedAmount(
                            limit.getUsedAmount().add(amount));
                    creditLimitRepository.save(limit);
                }

                // records an approval for used requests
                if (status == CreditRequestStatus.USED) {
                    ApprovalDecision decision = new ApprovalDecision();
                    decision.setCreditRequest(request);
                    decision.setDecidedBy(riskOfficer);
                    decision.setDecision(ApprovalDecisionType.APPROVED);
                    decision.setReason(
                            "Demo: approved after credit risk review");
                    decision.setCreatedAt(submittedAt.plusHours(3));
                    approvalDecisionRepository.save(decision);
                }

                // records a rejection for rejected requests
                if (status == CreditRequestStatus.REJECTED) {
                    ApprovalDecision decision = new ApprovalDecision();
                    decision.setCreditRequest(request);
                    decision.setDecidedBy(riskOfficer);
                    decision.setDecision(ApprovalDecisionType.REJECTED);
                    decision.setReason(
                            "Demo: rejected after credit risk review");
                    decision.setCreatedAt(submittedAt.plusHours(3));
                    approvalDecisionRepository.save(decision);
                }

                created++;
            }
        }

        System.out.println(
                "Demo credit requests created for "
                        + bankName + ": " + created);
    }
}
