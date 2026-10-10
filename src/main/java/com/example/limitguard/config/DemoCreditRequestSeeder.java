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
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Profile("dev")
public class DemoCreditRequestSeeder {

    private static final Set<String> DEMO_BANKS = Set.of(
            "Pearl Gulf Commercial Bank", "Al Noor Investment Bank",
            "Bahrain Meridian Bank", "Khaleej Capital Bank",
            "Gulf Horizon Financial Group", "Crescent International Bank"
    );

    private final CreditLimitRepository creditLimitRepository;
    private final CreditRequestRepository creditRequestRepository;
    private final ApprovalDecisionRepository approvalDecisionRepository;
    private final UserRepository userRepository;

    public DemoCreditRequestSeeder(CreditLimitRepository creditLimitRepository,
                                   CreditRequestRepository creditRequestRepository,
                                   ApprovalDecisionRepository approvalDecisionRepository,
                                   UserRepository userRepository) {
        this.creditLimitRepository = creditLimitRepository;
        this.creditRequestRepository = creditRequestRepository;
        this.approvalDecisionRepository = approvalDecisionRepository;
        this.userRepository = userRepository;
    }

    // runs after the existing institution, counterparty, limit and employee seeder
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seedCreditRequests() {

        // identifies limits that already have transactions so their balances remain untouched
        Set<Long> occupiedLimitIds = creditRequestRepository.findAll().stream()
                .map(request -> request.getCreditLimit().getId())
                .collect(Collectors.toSet());

        // only selects active counterparties belonging to the six fictional banks
        List<CreditLimit> demoLimits = creditLimitRepository.findAll().stream()
                .filter(limit -> DEMO_BANKS.contains(limit.getFinancialInstitution().getName()))
                .filter(limit -> limit.getCounterparty().getStatus() == CounterpartyStatus.ACTIVE)
                .sorted(Comparator.comparing(limit -> limit.getFinancialInstitution().getName()
                        + "|" + limit.getCounterparty().getName()))
                .toList();

        // keeps demo employees grouped by bank and role for correct ownership and approvals
        Map<Long, List<User>> usersByBank = userRepository.findAll().stream()
                .filter(user -> user.getEmail().matches("demo\\.employee\\d+@example\\.com"))
                .collect(Collectors.groupingBy(user -> user.getFinancialInstitution().getId()));

        // prepares 120 historical requests with the planned status distribution
        List<CreditRequestStatus> statuses = new ArrayList<>();
        addStatuses(statuses, CreditRequestStatus.RESERVED, 35);
        addStatuses(statuses, CreditRequestStatus.USED, 25);
        addStatuses(statuses, CreditRequestStatus.PENDING_APPROVAL, 20);
        addStatuses(statuses, CreditRequestStatus.CANCELLED, 15);
        addStatuses(statuses, CreditRequestStatus.REJECTED, 15);
        addStatuses(statuses, CreditRequestStatus.EXPIRED, 10);

        // distributes each status across the active limits instead of grouping by bank
        int created = 0;
        for (int index = 0; index < statuses.size(); index++) {
            if (demoLimits.isEmpty()) {
                break;
            }

            CreditLimit limit = demoLimits.get(index % demoLimits.size());
            if (occupiedLimitIds.contains(limit.getId())) {
                continue;
            }

            List<User> bankUsers = usersByBank.getOrDefault(
                    limit.getFinancialInstitution().getId(), List.of());
            List<User> managers = bankUsers.stream()
                    .filter(user -> user.getRole() == UserRole.RELATIONSHIP_MANAGER)
                    .sorted(Comparator.comparing(User::getEmail))
                    .toList();
            List<User> riskOfficers = bankUsers.stream()
                    .filter(user -> user.getRole() == UserRole.RISK_OFFICER)
                    .sorted(Comparator.comparing(User::getEmail))
                    .toList();

            // skips incomplete demo banks rather than creating invalid ownership links
            if (managers.isEmpty() || riskOfficers.isEmpty()) {
                continue;
            }

            CreditRequestStatus status = statuses.get(index);
            BigDecimal amount = BigDecimal.valueOf(10_000L + (index % 4) * 5_000L);

            // ensures each new active allocation fits the limit's remaining headroom
            if (status == CreditRequestStatus.RESERVED || status == CreditRequestStatus.USED) {
                BigDecimal remaining = limit.getLimitAmount()
                        .subtract(limit.getUsedAmount())
                        .subtract(limit.getReservedAmount());
                if (remaining.compareTo(amount) < 0) {
                    continue;
                }
            }

            CreditRequest request = new CreditRequest();
            request.setCreditLimit(limit);
            request.setRequester(managers.get(index % managers.size()));
            request.setAmount(amount);
            request.setStatus(status);

            // reserved requests remain valid; expired requests have a past expiry time
            if (status == CreditRequestStatus.RESERVED) {
                request.setExpiresAt(LocalDateTime.now().plusDays(30));
                limit.setReservedAmount(limit.getReservedAmount().add(amount));
            } else if (status == CreditRequestStatus.USED) {
                limit.setUsedAmount(limit.getUsedAmount().add(amount));
            } else if (status == CreditRequestStatus.EXPIRED) {
                request.setExpiresAt(LocalDateTime.now().minusDays(2));
            }

            creditRequestRepository.save(request);

            // rejected requests receive a separate decision made by a risk officer
            if (status == CreditRequestStatus.REJECTED) {
                ApprovalDecision decision = new ApprovalDecision();
                decision.setCreditRequest(request);
                decision.setDecidedBy(riskOfficers.get(index % riskOfficers.size()));
                decision.setDecision(ApprovalDecisionType.REJECTED);
                decision.setReason("Demo: request declined after counterparty risk review");
                approvalDecisionRepository.save(decision);
            }

            // saves exposure changes only for reserved and used requests
            if (status == CreditRequestStatus.RESERVED || status == CreditRequestStatus.USED) {
                creditLimitRepository.save(limit);
            }
            created++;
        }

        System.out.println("Demo credit requests created: " + created);
    }

    // adds the requested number of records for a given workflow status
    private void addStatuses(List<CreditRequestStatus> statuses,
                             CreditRequestStatus status, int count) {
        for (int i = 0; i < count; i++) {
            statuses.add(status);
        }
    }
}
