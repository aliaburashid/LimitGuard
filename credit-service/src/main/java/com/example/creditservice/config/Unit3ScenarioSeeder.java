package com.example.creditservice.config;

import lombok.RequiredArgsConstructor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;


// This class creates special credit requests for Unit 3 concurrency testing.
// It allows us to test two Risk Officers making decisions at the same time.
@Component
@Profile("demo & !prod & !production")
@ConditionalOnProperty(
        name = "limitguard.seed.unit3.enabled",
        havingValue = "true"
)
@RequiredArgsConstructor
public class Unit3ScenarioSeeder {

    // Citi's existing institution ID.
    private static final long CITI = 5L;

    // Citi Relationship Managers used for the test scenarios.
    private static final String PRIMARY_RM = "aliaburashid@hotmail.com";
    private static final String SECOND_RM = "sara.alhaddad@example.com";

    // Prefix used to identify Unit 3 test requests.
    private static final String PREFIX = "unit3-citi-";

    // Used to execute SQL queries against PostgreSQL.
    private final JdbcTemplate jdbc;


    // ---------------------------------------------------------
    // 1. CREATE THE UNIT 3 TEST SCENARIOS
    // ---------------------------------------------------------

    @Transactional
    public void seedScenarios() {

        // Only allow this seeder to run on a disposable database.
        // This prevents accidental changes to the development database.
        require(
                !"limitguard".equals(
                        jdbc.queryForObject(
                                "SELECT current_database()",
                                String.class
                        )
                ),
                "Use a disposable database for the Unit 3 scenario"
        );


        // Check that Citi exists with ID 5 and is active.
        require(
                count("""
                        SELECT count(*)
                        FROM public.financial_institutions
                        WHERE id = 5
                          AND name = 'Citi'
                          AND status = 'ACTIVE'
                        """) == 1,
                "Active Citi institution #5 is required"
        );


        // Wait at most five seconds when trying to acquire database locks.
        jdbc.execute("SET LOCAL lock_timeout = '5s'");

        // Lock the relevant tables while preparing the scenarios.
        // This prevents other transactions from changing the data at the same time.
        jdbc.execute("""
                LOCK TABLE
                    public.counterparties,
                    public.corporate_group_memberships,
                    public.group_credit_limits,
                    public.credit_limits,
                    public.credit_requests
                IN SHARE ROW EXCLUSIVE MODE
                """);


        // Find the two Citi Relationship Managers.
        long primaryRm = findManager(PRIMARY_RM);
        long secondRm = findManager(SECOND_RM);


        // Find Bahrain Trading Company's existing credit facility.
        long bahrainTrading = findFacility(
                "Bahrain Trading Company",
                "Bahrain Holding Group"
        );


        // Define the four Unit 3 test requests.
        List<ScenarioRequest> scenarios = List.of(

                // Historical used credit request for Falcon Freight.
                new ScenarioRequest(
                        PREFIX + "falcon-used",
                        findFacility(
                                "Falcon Freight Services",
                                "Gulf Investment Group"
                        ),
                        primaryRm,
                        300000,
                        true
                ),

                // Historical used credit request for Bahrain Gateway.
                new ScenarioRequest(
                        PREFIX + "gateway-used",
                        findFacility(
                                "Bahrain Gateway Warehousing",
                                "Gulf Investment Group"
                        ),
                        secondRm,
                        225000,
                        true
                ),

                // Pending request for testing two simultaneous approvals.
                new ScenarioRequest(
                        PREFIX + "dual-approval",
                        bahrainTrading,
                        primaryRm,
                        600000,
                        false
                ),

                // Pending request for testing approval versus rejection.
                new ScenarioRequest(
                        PREFIX + "approval-versus-rejection",
                        bahrainTrading,
                        primaryRm,
                        600000,
                        false
                )
        );


        // Check whether Unit 3 requests already exist.
        long existing = count("""
                SELECT count(*)
                FROM public.credit_requests
                WHERE seed_key LIKE 'unit3-citi-%'
                """);


        // If the requests exist, validate them instead of inserting duplicates.
        if (existing > 0) {
            verifyExistingRequests(scenarios, existing);
            return;
        }


        // Check that Bahrain Trading has enough available credit
        // for both pending approval demonstrations.
        verifyHeadroom(bahrainTrading, 1200000);

        // Check that Bahrain Holding Group has enough available headroom.
        verifyGroupHeadroom(bahrainTrading, 1200000);

        // Check group headroom for the historical used requests.
        verifyGroupHeadroom(
                scenarios.get(0).facilityId(),
                525000
        );


        // Get the current time without nanoseconds.
        LocalDateTime now = LocalDateTime.now().withNano(0);


        // Create each scenario request.
        for (ScenarioRequest scenario : scenarios) {

            if (scenario.used()) {

                // Check that the facility can support this used amount.
                verifyHeadroom(
                        scenario.facilityId(),
                        scenario.amount()
                );

                // Increase the facility's used balance.
                addUsedBalance(scenario);
            }

            // Save the historical or pending credit request.
            insertRequest(scenario, now);
        }
    }


    // ---------------------------------------------------------
    // 2. FIND A CITI RELATIONSHIP MANAGER
    // ---------------------------------------------------------

    private long findManager(String email) {

        // Find an active, verified Relationship Manager belonging to Citi.
        List<Long> ids = jdbc.queryForList("""
                SELECT id
                FROM public.users
                WHERE email = ?
                  AND financial_institution_id = ?
                  AND role = 'RELATIONSHIP_MANAGER'
                  AND status = 'ACTIVE'
                  AND email_verified
                """,
                Long.class,
                email,
                CITI
        );


        // Exactly one matching manager must exist.
        require(
                ids.size() == 1,
                "Missing or ambiguous Citi manager: " + email
        );

        return ids.get(0);
    }


    // ---------------------------------------------------------
    // 3. FIND A COMPANY'S CREDIT FACILITY
    // ---------------------------------------------------------

    private long findFacility(String company, String group) {

        // Find the company's Citi credit facility and corporate group.
        List<Long> ids = jdbc.queryForList("""
                SELECT f.id
                FROM public.credit_limits f
                JOIN public.counterparties c
                    ON c.id = f.counterparty_id
                JOIN public.corporate_group_memberships m
                    ON m.counterparty_id = c.id
                JOIN public.corporate_groups g
                    ON g.id = m.corporate_group_id
                WHERE f.financial_institution_id = ?
                  AND c.name = ?
                  AND c.status = 'ACTIVE'
                  AND g.name = ?
                """,
                Long.class,
                CITI,
                company,
                group
        );


        // The Citi portfolio must already contain this facility.
        require(
                ids.size() == 1,
                "Seed the Citi portfolio first. Missing or ambiguous facility: "
                        + company
        );

        return ids.get(0);
    }


    // ---------------------------------------------------------
    // 4. CHECK AVAILABLE FACILITY HEADROOM
    // ---------------------------------------------------------

    private void verifyHeadroom(long facility, int amount) {

        // Check that the facility has enough available credit.
        // Also check that used and reserved balances match the request ledger.
        require(
                count("""
                        SELECT count(*)
                        FROM public.credit_limits f
                        WHERE id = ?
                          AND limit_amount - used_amount - reserved_amount >= ?
                          AND used_amount = (
                              SELECT coalesce(sum(amount), 0)
                              FROM public.credit_requests
                              WHERE credit_limit_id = f.id
                                AND status = 'USED'
                          )
                          AND reserved_amount = (
                              SELECT coalesce(sum(amount), 0)
                              FROM public.credit_requests
                              WHERE credit_limit_id = f.id
                                AND status = 'RESERVED'
                          )
                        """,
                        facility,
                        amount
                ) == 1,
                "Insufficient headroom or inconsistent Unit 3 facility ledger"
        );
    }


    // ---------------------------------------------------------
    // 5. CHECK AVAILABLE CORPORATE GROUP HEADROOM
    // ---------------------------------------------------------

    private void verifyGroupHeadroom(long facility, int amount) {

        // Calculate the group's remaining credit after used and reserved amounts.
        List<BigDecimal> headroom = jdbc.queryForList("""
                SELECT
                    g.amount - coalesce(
                        sum(
                            member_limit.used_amount
                            + member_limit.reserved_amount
                        ),
                        0
                    )
                FROM public.credit_limits f
                JOIN public.corporate_group_memberships m
                    ON m.counterparty_id = f.counterparty_id
                JOIN public.group_credit_limits g
                    ON g.corporate_group_id = m.corporate_group_id
                    AND g.financial_institution_id = f.financial_institution_id
                JOIN public.corporate_group_memberships member
                    ON member.corporate_group_id = g.corporate_group_id
                LEFT JOIN public.credit_limits member_limit
                    ON member_limit.counterparty_id = member.counterparty_id
                    AND member_limit.financial_institution_id = g.financial_institution_id
                WHERE f.id = ?
                GROUP BY g.id, g.amount
                """,
                BigDecimal.class,
                facility
        );


        // Check that exactly one group facility was found
        // and that its available headroom is sufficient.
        require(
                headroom.size() == 1
                        && headroom.get(0).compareTo(
                        BigDecimal.valueOf(amount)
                ) >= 0,
                "Insufficient headroom or ambiguous Unit 3 group facility"
        );
    }


    // ---------------------------------------------------------
    // 6. VERIFY EXISTING UNIT 3 REQUESTS
    // ---------------------------------------------------------

    private void verifyExistingRequests(
            List<ScenarioRequest> scenarios,
            long existing
    ) {

        // All four scenarios must exist together.
        require(
                existing == scenarios.size(),
                "Partial or conflicting Unit 3 scenarios"
        );


        // Check that each request has the correct identity,
        // facility, requester and amount.
        for (ScenarioRequest scenario : scenarios) {

            require(
                    count("""
                            SELECT count(*)
                            FROM public.credit_requests
                            WHERE seed_key = ?
                              AND credit_limit_id = ?
                              AND requester_id = ?
                              AND amount = ?
                            """,
                            scenario.key(),
                            scenario.facilityId(),
                            scenario.requester(),
                            scenario.amount()
                    ) == 1,
                    "Conflicting Unit 3 scenario: " + scenario.key()
            );
        }

        // Do not reset request statuses.
        // Live approvals and rejections must remain unchanged.
    }


    // ---------------------------------------------------------
    // 7. UPDATE USED CREDIT BALANCE
    // ---------------------------------------------------------

    private void addUsedBalance(ScenarioRequest scenario) {

        // Increase the facility's used amount for a historical request.
        jdbc.update("""
                UPDATE public.credit_limits
                SET used_amount = used_amount + ?,
                    updated_at = LOCALTIMESTAMP
                WHERE id = ?
                """,
                scenario.amount(),
                scenario.facilityId()
        );
    }


    // ---------------------------------------------------------
    // 8. INSERT A UNIT 3 CREDIT REQUEST
    // ---------------------------------------------------------

    private void insertRequest(
            ScenarioRequest scenario,
            LocalDateTime now
    ) {

        // Historical requests were submitted seven days ago.
        // Pending requests use the current date and time.
        LocalDateTime submitted = scenario.used()
                ? now.minusDays(7)
                : now;


        // Insert the request with the correct status and timestamps.
        jdbc.update("""
                INSERT INTO public.credit_requests (
                    seed_key,
                    credit_limit_id,
                    requester_id,
                    amount,
                    status,
                    created_at,
                    updated_at,
                    expires_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                scenario.key(),
                scenario.facilityId(),
                scenario.requester(),
                scenario.amount(),
                scenario.used() ? "USED" : "PENDING_APPROVAL",
                submitted,
                scenario.used() ? submitted.plusHours(2) : submitted,
                scenario.used() ? submitted.plusHours(24) : null
        );
    }


    // ---------------------------------------------------------
    // 9. HELPER METHOD TO COUNT DATABASE RECORDS
    // ---------------------------------------------------------

    private long count(String sql, Object... arguments) {

        // Execute a SQL count query and return the result.
        return jdbc.queryForObject(
                sql,
                Long.class,
                arguments
        );
    }


    // ---------------------------------------------------------
    // 10. HELPER METHOD FOR VALIDATION
    // ---------------------------------------------------------

    private void require(boolean condition, String message) {

        // Stop seeding if a required condition is not met.
        // The transaction will roll back when an exception is thrown.
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }


    // Stores the details of one Unit 3 credit request.
    // "used" indicates whether it is a historical used request.
    private record ScenarioRequest(
            String key,
            long facilityId,
            long requester,
            int amount,
            boolean used
    ) {
    }
}
