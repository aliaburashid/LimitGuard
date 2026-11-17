package com.example.creditservice.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;


// This class creates the demo credit portfolio for Gulf International Bank.
// It only runs when the demo profile and GIB seeding property are enabled.
@Component
@Profile("demo & !prod & !production")
@ConditionalOnProperty(
        name = "limitguard.seed.gib.enabled",
        havingValue = "true"
)
@RequiredArgsConstructor
@Slf4j
public class GibPortfolioSeeder {

    // Name of the corporate group used in the GIB demo.
    private static final String GROUP_NAME = "North Reef Holdings";

    // Possible names used to find GIB in the database.
    private static final String GIB_NAMES =
            "'gulf international bank', 'gulf international bank (gib)', 'gib'";

    // User roles required for the demo.
    private static final String RM = "RELATIONSHIP_MANAGER";
    private static final String RO = "RISK_OFFICER";


    // Used to execute SQL queries against PostgreSQL.
    private final JdbcTemplate jdbc;

    // Helper class used to execute SQL fixture files.
    private final DemoSeedSql sql;


    // ---------------------------------------------------------
    // 1. SEED GIB'S DEMO PORTFOLIO
    // ---------------------------------------------------------

    @Transactional
    public void seedPortfolio() {

        // Prevent two GIB seeders from inserting data at the same time.
        jdbc.execute(
                "SELECT pg_advisory_xact_lock(5500197606)"
        );

        // Find GIB and get its institution ID.
        long bankId = findGibInstitution();

        // Find the required GIB users and prepare temporary seed data.
        prepareSeedContext(bankId);

        // Load the six companies, group and credit request definitions.
        sql.execute("config/demo/gib-fixtures.sql");


        // If demo requests already exist, validate the portfolio.
        // Do not recreate requests or overwrite live decisions.
        if (hasSeededRequests()) {

            verifySeededPortfolio(bankId);

            log.info("GIB demonstration portfolio already exists");

            return;
        }


        // Make sure the demo company and group names do not conflict
        // with existing records belonging to other banks.
        checkForNameConflicts();

        // Insert the GIB companies, financial profiles, facilities,
        // credit requests, decisions and exposure history.
        sql.execute("config/demo/gib-insert.sql");

        // Check that the inserted portfolio is complete.
        verifySeededPortfolio(bankId);

        log.info(
                "Prepared the GIB demonstration portfolio for institution {}",
                bankId
        );
    }


    // ---------------------------------------------------------
    // 2. FIND GIB IN THE DATABASE
    // ---------------------------------------------------------

    private long findGibInstitution() {

        // Auth Service owns the bank records.
        // Credit Service only reads the institution ID.
        List<Long> ids = jdbc.queryForList("""
                SELECT id
                FROM public.financial_institutions
                WHERE lower(trim(name)) IN (%s)
                """.formatted(GIB_NAMES), Long.class);


        // Exactly one GIB institution must exist.
        require(
                ids.size() == 1,
                "Run Auth demo seeding first. Exactly one GIB institution is required."
        );

        long bankId = ids.get(0);


        // Make sure GIB is active and is not using a Citi institution ID.
        Boolean isActive = jdbc.queryForObject("""
                SELECT status = 'ACTIVE'
                FROM public.financial_institutions
                WHERE id = ?
                """, Boolean.class, bankId);

        require(
                bankId != 5L
                        && bankId != 13L
                        && Boolean.TRUE.equals(isActive),
                "GIB must be active and distinct from Citi"
        );

        return bankId;
    }


    // ---------------------------------------------------------
    // 3. PREPARE THE GIB SEED CONTEXT
    // ---------------------------------------------------------

    private void prepareSeedContext(long bankId) {

        // Find the two Relationship Managers.
        long rm1 = requireAccount(
                bankId,
                "gib.noura.almarafi@example.com",
                RM
        );

        long rm2 = requireAccount(
                bankId,
                "gib.yousef.alkindi@example.com",
                RM
        );


        // Find the two Risk Officers.
        long ro1 = requireAccount(
                bankId,
                "gib.layla.almansoori@example.com",
                RO
        );

        long ro2 = requireAccount(
                bankId,
                "gib.hassan.aldarwish@example.com",
                RO
        );


        // Create a temporary table to store the bank and user IDs.
        // The table is automatically removed when the transaction commits.
        jdbc.execute("""
                CREATE TEMP TABLE gib_seed_context (
                    bank_id bigint,
                    rm1 bigint,
                    rm2 bigint,
                    ro1 bigint,
                    ro2 bigint,
                    local_now timestamp,
                    reporting_date date
                ) ON COMMIT DROP
                """);


        // Store the bank IDs, user IDs and current timestamps.
        // The SQL fixture files will use these values.
        jdbc.update("""
                INSERT INTO gib_seed_context
                SELECT
                    ?, ?, ?, ?, ?,
                    transaction_timestamp() AT TIME ZONE 'Asia/Bahrain',
                    (transaction_timestamp() AT TIME ZONE 'UTC')::date
                """,
                bankId,
                rm1,
                rm2,
                ro1,
                ro2
        );
    }


    // ---------------------------------------------------------
    // 4. VALIDATE A REQUIRED GIB USER
    // ---------------------------------------------------------

    private long requireAccount(
            long bankId,
            String email,
            String role
    ) {

        // Find a user belonging to GIB with the correct email and role.
        // The account must be active and email-verified.
        List<Long> ids = jdbc.queryForList("""
                SELECT id
                FROM public.users
                WHERE financial_institution_id = ?
                  AND email = ?
                  AND role = ?
                  AND status = 'ACTIVE'
                  AND email_verified
                """,
                Long.class,
                bankId,
                email,
                role
        );


        // Stop seeding if the required user is missing or duplicated.
        require(
                ids.size() == 1,
                "Required active, verified GIB demo account is missing: " + email
        );

        return ids.get(0);
    }


    // ---------------------------------------------------------
    // 5. CHECK IF GIB REQUESTS ALREADY EXIST
    // ---------------------------------------------------------

    private boolean hasSeededRequests() {

        // Search for credit requests created by the GIB demo seeder.
        // The seed_key identifies these requests across multiple runs.
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                    FROM public.credit_requests
                    WHERE seed_key LIKE 'gib-demo-v1-%'
                )
                """, Boolean.class));
    }


    // ---------------------------------------------------------
    // 6. CHECK FOR COMPANY AND GROUP NAME CONFLICTS
    // ---------------------------------------------------------

    private void checkForNameConflicts() {

        // Company and group names are shared across institutions.
        // Stop if the demo names already exist without seeded requests.
        // This avoids accidentally modifying another bank's portfolio.
        Boolean noConflicts = jdbc.queryForObject("""
                SELECT NOT EXISTS (
                    SELECT 1
                    FROM gib_companies s
                    JOIN public.counterparties c
                        ON lower(trim(c.name)) = lower(s.name)

                    UNION ALL

                    SELECT 1
                    FROM public.corporate_groups
                    WHERE lower(trim(name)) = lower(?)
                )
                """,
                Boolean.class,
                GROUP_NAME
        );

        require(
                Boolean.TRUE.equals(noConflicts),
                "GIB demo company or group name already exists without matching seeded requests"
        );
    }


    // ---------------------------------------------------------
    // 7. VERIFY THE GIB DEMO PORTFOLIO
    // ---------------------------------------------------------

    private void verifySeededPortfolio(long bankId) {

        // Check that all six companies have:
        // - A GIB credit facility.
        // - Six complete financial ratios.
        // - The correct corporate group membership.
        // - No credit facilities belonging to another bank.
        Integer companyCount = jdbc.queryForObject("""
                SELECT count(*)
                FROM gib_companies s
                JOIN public.counterparties c USING (name)
                JOIN public.credit_limits f
                    ON f.counterparty_id = c.id
                    AND f.financial_institution_id = ?
                JOIN public.counterparty_financial_profiles p
                    ON p.counterparty_id = c.id
                LEFT JOIN public.corporate_group_memberships m
                    ON m.counterparty_id = c.id
                LEFT JOIN public.corporate_groups g
                    ON g.id = m.corporate_group_id
                WHERE (
                    (s.grouped AND g.name = ?)
                    OR (NOT s.grouped AND m.id IS NULL)
                )
                  AND num_nonnulls(
                      p.return_on_assets,
                      p.profit_margin,
                      p.fixed_asset_turnover,
                      p.debt_to_total_capital_percent,
                      p.interest_coverage,
                      p.total_debt_to_ebitda
                  ) = 6
                  AND NOT EXISTS (
                      SELECT 1
                      FROM public.credit_limits other
                      WHERE other.counterparty_id = c.id
                        AND other.financial_institution_id <> f.financial_institution_id
                  )
                """,
                Integer.class,
                bankId,
                GROUP_NAME
        );

        require(
                Integer.valueOf(6).equals(companyCount),
                "GIB demo catalog is incomplete or has conflicting ownership"
        );


        // Verify that North Reef Holdings has exactly one GIB group facility.
        Integer groupCount = jdbc.queryForObject("""
                SELECT count(*)
                FROM public.group_credit_limits l
                JOIN public.corporate_groups g
                    ON g.id = l.corporate_group_id
                WHERE l.financial_institution_id = ?
                  AND g.name = ?
                """,
                Integer.class,
                bankId,
                GROUP_NAME
        );

        require(
                Integer.valueOf(1).equals(groupCount),
                "GIB demo group facility is missing or duplicated"
        );


        // Verify all 13 demo credit requests.
        // Check their original identities, amounts, requesters and facilities.
        // Allow statuses to change after live approvals, rejections or expiry.
        Integer requestCount = jdbc.queryForObject("""
                SELECT count(*)
                FROM gib_requests r
                JOIN gib_companies s
                    ON s.code = r.company_code
                JOIN public.counterparties c USING (name)
                JOIN public.credit_requests actual USING (seed_key)
                CROSS JOIN gib_seed_context seed
                JOIN public.credit_limits f
                    ON f.id = actual.credit_limit_id
                    AND f.counterparty_id = c.id
                    AND f.financial_institution_id = seed.bank_id
                WHERE actual.amount = r.amount
                  AND actual.requester_id = CASE r.rm_number
                      WHEN 1 THEN seed.rm1
                      ELSE seed.rm2
                  END
                """, Integer.class);

        require(
                Integer.valueOf(13).equals(requestCount),
                "GIB demo requests are incomplete or reference conflicting records"
        );
    }


    // ---------------------------------------------------------
    // 8. HELPER METHOD FOR VALIDATION
    // ---------------------------------------------------------

    private void require(boolean condition, String message) {

        // Stop seeding when a required condition is not met.
        // The transaction will roll back if an exception is thrown.
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
