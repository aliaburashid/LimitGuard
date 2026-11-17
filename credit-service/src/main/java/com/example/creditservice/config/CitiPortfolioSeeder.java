package com.example.creditservice.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;


// This class seeds Citi's demo portfolio into the database.
// It creates missing demo data while preserving existing records.
@Component
@Profile("demo & !prod & !production")
@ConditionalOnProperty(
        name = "limitguard.seed.citi.enabled",
        havingValue = "true"
)
@RequiredArgsConstructor
@Slf4j
public class CitiPortfolioSeeder {

    // Used to execute SQL queries against PostgreSQL.
    private final JdbcTemplate jdbc;

    // Helper class used to execute SQL fixture files.
    private final DemoSeedSql sql;


    // ---------------------------------------------------------
    // 1. SEED CITI'S DEMO PORTFOLIO
    // ---------------------------------------------------------

    @Transactional
    public void seedPortfolio() {

        // Prevent multiple Citi seeders from running at the same time.
        jdbc.execute(
                "SELECT pg_advisory_xact_lock(5500197605)"
        );

        // Check that Citi exists with ID 5 and is active.
        require("""
                SELECT count(*) = 1
                FROM public.financial_institutions
                WHERE id = 5
                  AND lower(trim(name)) = 'citi'
                  AND status = 'ACTIVE'
                """,
                "Citi portfolio seed requires active Citi institution ID 5"
        );

        // Load the original Citi companies, groups and demo fixtures.
        sql.execute("config/citi-panel/fixtures.sql");

        // Load additional companies and relationships from the old system.
        sql.execute("config/demo/citi-legacy.sql");

        // Load synthetic financial ratios for Citi companies.
        sql.execute("config/demo/citi-synthetic-profiles.sql");

        // Check existing company and group relationships before merging.
        verifyExistingRelationships();


        // Find companies whose financial profiles are incomplete.
        // Some of these profiles may also be visible to other banks.
        var sharedBackfills = jdbc.queryForList("""
                SELECT s.name
                FROM seed_companies s
                JOIN public.counterparties c
                    ON lower(trim(c.name)) = lower(s.name)
                LEFT JOIN public.counterparty_financial_profiles p
                    ON p.counterparty_id = c.id
                WHERE num_nonnulls(
                    p.return_on_assets,
                    p.profit_margin,
                    p.fixed_asset_turnover,
                    p.debt_to_total_capital_percent,
                    p.interest_coverage,
                    p.total_debt_to_ebitda,
                    p.financial_year_end
                ) < 7
                  AND EXISTS (
                      SELECT 1
                      FROM public.credit_limits f
                      WHERE f.counterparty_id = c.id
                        AND f.financial_institution_id <> 5
                  )
                ORDER BY s.name
                """, String.class);


        // Insert missing Citi portfolio records and complete profiles.
        // Existing credit balances and decisions are preserved.
        sql.execute("config/demo/citi-merge.sql");


        // Check that all 59 Citi companies have facilities
        // and complete financial profiles.
        require("""
                SELECT count(*) = 59
                FROM citi_seed_catalog s
                JOIN public.credit_limits l
                    ON l.counterparty_id = s.company_id
                    AND l.financial_institution_id = 5
                JOIN public.counterparty_financial_profiles p
                    ON p.counterparty_id = s.company_id
                WHERE num_nonnulls(
                    p.return_on_assets,
                    p.profit_margin,
                    p.fixed_asset_turnover,
                    p.debt_to_total_capital_percent,
                    p.interest_coverage,
                    p.total_debt_to_ebitda,
                    p.financial_year_end
                ) = 7
                """,
                "Citi portfolio must contain 59 facilities with complete profiles"
        );


        // Check that Citi has credit facilities for all 10 groups.
        require("""
                SELECT count(*) = 10
                FROM seed_groups s
                JOIN public.corporate_groups g
                    ON lower(trim(g.name)) = lower(s.name)
                JOIN public.group_credit_limits l
                    ON l.corporate_group_id = g.id
                    AND l.financial_institution_id = 5
                """,
                "Citi portfolio must contain ten group facilities"
        );


        // Remove temporary tables created by the SQL fixtures.
        // This prevents them from remaining in the pooled connection.
        jdbc.execute("""
                DROP TABLE
                    pg_temp.seed_named_profiles,
                    pg_temp.seed_profiles,
                    pg_temp.seed_companies,
                    pg_temp.seed_groups
                """);


        // Log any completed profiles that are also visible to other banks.
        if (!sharedBackfills.isEmpty()) {
            log.info(
                    "Citi seed completed synthetic company profiles visible to other institutions: {}",
                    sharedBackfills
            );
        }
    }


    // ---------------------------------------------------------
    // 2. VALIDATE EXISTING COMPANY AND GROUP RELATIONSHIPS
    // ---------------------------------------------------------

    private void verifyExistingRelationships() {

        // Make sure company and group names do not match multiple records.
        // This prevents the seeder from using the wrong company or group.
        require("""
                SELECT NOT EXISTS (
                    SELECT 1
                    FROM seed_companies s
                    JOIN public.counterparties c
                        ON lower(trim(c.name)) = lower(s.name)
                    GROUP BY s.name
                    HAVING count(*) > 1

                    UNION ALL

                    SELECT 1
                    FROM seed_groups s
                    JOIN public.corporate_groups g
                        ON lower(trim(g.name)) = lower(s.name)
                    GROUP BY s.name
                    HAVING count(*) > 1
                )
                """,
                "Ambiguous counterparty or group identity in Citi portfolio"
        );


        // Check that existing companies belong to the expected groups.
        // Stop if a company is already assigned to a different group.
        require("""
                SELECT NOT EXISTS (
                    SELECT 1
                    FROM seed_companies s
                    JOIN public.counterparties c
                        ON lower(trim(c.name)) = lower(s.name)
                    JOIN public.corporate_group_memberships m
                        ON m.counterparty_id = c.id
                    JOIN public.corporate_groups g
                        ON g.id = m.corporate_group_id
                    WHERE lower(trim(g.name))
                        IS DISTINCT FROM lower(s.group_name)
                )
                """,
                "Existing Citi portfolio membership differs from the fixture"
        );


        // Prevent changes to group membership that could affect
        // another bank's credit exposure calculations.
        require("""
                SELECT NOT EXISTS (
                    SELECT 1
                    FROM seed_companies s
                    JOIN public.counterparties c
                        ON lower(trim(c.name)) = lower(s.name)
                    WHERE s.group_name IS NOT NULL
                      AND NOT EXISTS (
                          SELECT 1
                          FROM public.corporate_group_memberships m
                          WHERE m.counterparty_id = c.id
                      )
                      AND EXISTS (
                          SELECT 1
                          FROM public.credit_limits l
                          WHERE l.counterparty_id = c.id
                            AND l.financial_institution_id <> 5
                      )
                )
                """,
                "Adding the fixture membership would change another institution's group exposure"
        );
    }


    // ---------------------------------------------------------
    // 3. HELPER METHOD FOR DATABASE VALIDATION
    // ---------------------------------------------------------

    private void require(String sql, String message) {

        // Execute a SQL query that must return true.
        Boolean valid = jdbc.queryForObject(
                sql,
                Boolean.class
        );

        // Stop seeding if the database does not meet the requirement.
        // The transaction will roll back its changes.
        if (!Boolean.TRUE.equals(valid)) {
            throw new IllegalStateException(message);
        }
    }
}
