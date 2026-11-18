package com.example.authservice.config;

import com.example.authservice.enums.FinancialInstitutionStatus;
import com.example.authservice.enums.UserRole;
import com.example.authservice.enums.UserStatus;
import com.example.authservice.model.FinancialInstitution;
import com.example.authservice.model.User;
import com.example.authservice.repository.FinancialInstitutionRepository;
import com.example.authservice.repository.UserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;


// This class creates demo users and banks for testing LimitGuard
// It only runs when the "demo" profile and seeding property are enabled
@Component
@Profile("demo & !prod & !production")
@ConditionalOnProperty(
        name = "limitguard.seed.demo.enabled",
        havingValue = "true"
)
@RequiredArgsConstructor
public class DemoDataSeeder implements ApplicationRunner {

    // Existing Citi bank ID in the database.
    private static final long CITI_ID = 5L;

    // Name of the second demo bank.
    private static final String GIB_NAME = "Gulf International Bank";

    // PostgreSQL lock ID used to prevent two seeders from running at once.
    private static final long GIB_SEED_LOCK = 0x4c696d6974474942L;


    // Shared fallback password for every demo account (set in .env).
    // A per-account variable such as GIB_DEMO_RM1_PASSWORD takes priority.
    private static final String SHARED_PASSWORD_VARIABLE = "LIMITGUARD_DEMO_PASSWORD";

    // The four Citi demo users.
    // On an existing development database they must already exist and are only checked.
    // On a fresh database (for example Docker) they are created when
    // limitguard.seed.demo.provision-citi=true.
    private static final List<DemoUser> CITI_USERS = List.of(

            new DemoUser(
                    "Alia",
                    "Burashid",
                    "aliaburashid@hotmail.com",
                    UserRole.RELATIONSHIP_MANAGER,
                    "CITI_DEMO_RM1_PASSWORD"
            ),

            new DemoUser(
                    "Sara",
                    "Al Haddad",
                    "sara.alhaddad@example.com",
                    UserRole.RELATIONSHIP_MANAGER,
                    "CITI_DEMO_RM2_PASSWORD"
            ),

            new DemoUser(
                    "Citi",
                    "Risk Officer",
                    "burashid993@gmail.com",
                    UserRole.RISK_OFFICER,
                    "CITI_DEMO_RO1_PASSWORD"
            ),

            new DemoUser(
                    "Fatima",
                    "Al Zayani",
                    "fatima.alzayani@example.com",
                    UserRole.RISK_OFFICER,
                    "CITI_DEMO_RO2_PASSWORD"
            )
    );


    // These four demo users belong to Gulf International Bank.
    // Each user has a role and an environment variable for their password.
    private static final List<DemoUser> GIB_USERS = List.of(

            new DemoUser(
                    "Noura",
                    "Al Marafi",
                    "gib.noura.almarafi@example.com",
                    UserRole.RELATIONSHIP_MANAGER,
                    "GIB_DEMO_RM1_PASSWORD"
            ),

            new DemoUser(
                    "Yousef",
                    "Al Kindi",
                    "gib.yousef.alkindi@example.com",
                    UserRole.RELATIONSHIP_MANAGER,
                    "GIB_DEMO_RM2_PASSWORD"
            ),

            new DemoUser(
                    "Layla",
                    "Al Mansoori",
                    "gib.layla.almansoori@example.com",
                    UserRole.RISK_OFFICER,
                    "GIB_DEMO_RO1_PASSWORD"
            ),

            new DemoUser(
                    "Hassan",
                    "Al Darwish",
                    "gib.hassan.aldarwish@example.com",
                    UserRole.RISK_OFFICER,
                    "GIB_DEMO_RO2_PASSWORD"
            )
    );


    // Repositories allow us to find and save data in PostgreSQL.
    private final FinancialInstitutionRepository institutions;
    private final UserRepository users;

    // Converts passwords into secure hashes before saving them.
    private final PasswordEncoder passwordEncoder;

    // Allows us to execute the PostgreSQL locking query.
    private final JdbcTemplate jdbc;

    // When true, Citi (ID 5) and its four users are created if missing.
    // Off by default so an existing development database is only validated.
    @Value("${limitguard.seed.demo.provision-citi:false}")
    private boolean provisionCiti;


    // ---------------------------------------------------------
    // 1. MAIN METHOD - START THE DEMO SEEDING
    // ---------------------------------------------------------

    @Override
    @Transactional
    public void run(ApplicationArguments args) {

        // Prevent multiple seeders from modifying the data at the same time.
        jdbc.execute(
                "SELECT pg_advisory_xact_lock(" + GIB_SEED_LOCK + ")"
        );

        // On a fresh database, create Citi and its demo users first.
        if (provisionCiti) {
            provisionCitiIfMissing();
        }

        // Make sure Citi and its four demo users exist.
        validateExistingCitiAccounts();

        // Find GIB or create it if it does not exist.
        FinancialInstitution gib = findOrCreateGib();

        // Create each GIB user if the account does not already exist.
        for (DemoUser demo : GIB_USERS) {
            createDemoAccountIfMissing(gib, demo);
        }
    }


    // ---------------------------------------------------------
    // 2a. CREATE CITI AND ITS USERS ON A FRESH DATABASE
    // ---------------------------------------------------------

    private void provisionCitiIfMissing() {

        // Credit Service and the Citi fixtures expect Citi to have ID 5.
        if (institutions.findById(CITI_ID).isEmpty()) {

            // Never create a second Citi under another ID.
            if (!institutions.findByNormalizedNames(List.of("citi")).isEmpty()) {
                throw new IllegalStateException(
                        "A Citi institution exists with an ID other than 5"
                );
            }

            // The ID column is GENERATED BY DEFAULT, so an explicit ID is allowed.
            jdbc.update("""
                    INSERT INTO public.financial_institutions
                        (id, name, status, created_at, updated_at)
                    VALUES (?, 'Citi', 'ACTIVE', now(), now())
                    """, CITI_ID);

            // Move the ID sequence past 5 so later banks do not collide with Citi.
            jdbc.queryForObject("""
                    SELECT setval(
                        pg_get_serial_sequence('public.financial_institutions', 'id'),
                        GREATEST((SELECT max(id) FROM public.financial_institutions), 1)
                    )
                    """, Long.class);
        }

        FinancialInstitution citi = institutions.findById(CITI_ID)
                .orElseThrow(() -> new IllegalStateException(
                        "Citi institution ID 5 could not be created"
                ));

        for (DemoUser demo : CITI_USERS) {
            createDemoAccountIfMissing(citi, demo);
        }
    }


    // ---------------------------------------------------------
    // 2b. VALIDATE EXISTING CITI USERS
    // ---------------------------------------------------------

    private void validateExistingCitiAccounts() {

        // Find Citi using its existing database ID.
        FinancialInstitution citi = institutions.findById(CITI_ID)
                .orElseThrow(() -> new IllegalStateException(
                        "Citi institution ID 5 is required"
                ));

        // Make sure the bank is actually Citi and is active.
        if (!"Citi".equalsIgnoreCase(citi.getName().trim())
                || citi.getStatus() != FinancialInstitutionStatus.ACTIVE) {

            throw new IllegalStateException(
                    "Institution ID 5 must be active Citi"
            );
        }

        // Check that all four Citi users have the correct bank and role.
        for (DemoUser account : CITI_USERS) {

            List<User> matches = users.findByNormalizedEmail(
                    account.email()
            );

            if (!matchesExpectedAccount(
                    matches,
                    citi,
                    account.email(),
                    account.role()
            )) {

                throw new IllegalStateException(
                        "Missing or conflicting Citi demo account "
                                + account.email()
                );
            }
        }
    }


    // ---------------------------------------------------------
    // 3. FIND OR CREATE GULF INTERNATIONAL BANK
    // ---------------------------------------------------------

    private FinancialInstitution findOrCreateGib() {

        // Search for GIB using its possible names.
        List<FinancialInstitution> matches = institutions.findByNormalizedNames(
                List.of(
                        "gulf international bank",
                        "gulf international bank (gib)",
                        "gib"
                )
        );

        // Stop if multiple banks match GIB to avoid duplicates.
        if (matches.size() > 1) {
            throw new IllegalStateException(
                    "Multiple institutions match Gulf International Bank"
            );
        }

        // If GIB does not exist, create a new active institution.
        if (matches.isEmpty()) {

            FinancialInstitution bank = new FinancialInstitution();

            bank.setName(GIB_NAME);
            bank.setStatus(FinancialInstitutionStatus.ACTIVE);

            return institutions.save(bank);
        }

        // If GIB already exists, make sure it is active.
        FinancialInstitution bank = matches.get(0);

        if (bank.getStatus() != FinancialInstitutionStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Existing Gulf International Bank institution is not active"
            );
        }

        // Reuse the existing bank instead of creating another one.
        return bank;
    }


    // ---------------------------------------------------------
    // 4. CREATE A DEMO USER IF THE ACCOUNT DOES NOT EXIST
    // ---------------------------------------------------------

    private void createDemoAccountIfMissing(
            FinancialInstitution bank,
            DemoUser demo
    ) {

        // Check whether this email already exists in the database.
        List<User> matches = users.findByNormalizedEmail(
                demo.email()
        );

        if (!matches.isEmpty()) {

            // Reuse the user only if the bank, role and status are correct.
            if (!matchesExpectedAccount(
                    matches,
                    bank,
                    demo.email(),
                    demo.role()
            )) {

                throw new IllegalStateException(
                        "Existing user conflicts with " + bank.getName()
                                + " demo account " + demo.email()
                );
            }

            // The account already exists, so nothing needs to be created.
            return;
        }

        // Read the password from an environment variable.
        // This avoids storing demo passwords directly in the Java code.
        String password = resolvePassword(demo);

        // Create a new user and fill in their details.
        User user = new User();

        user.setFirstName(demo.firstName());
        user.setLastName(demo.lastName());
        user.setEmail(demo.email());

        // Assign the user to the bank with the correct role.
        user.setFinancialInstitution(bank);
        user.setRole(demo.role());

        // Activate the account and mark its email as verified.
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerified(true);

        // Hash the password using BCrypt before saving it.
        user.setPassword(passwordEncoder.encode(password));

        // Save the account directly without sending verification emails.
        users.save(user);
    }


    // ---------------------------------------------------------
    // 5. CHECK IF AN EXISTING ACCOUNT MATCHES OUR REQUIREMENTS
    // ---------------------------------------------------------

    private boolean matchesExpectedAccount(
            List<User> matches,
            FinancialInstitution bank,
            String email,
            UserRole role
    ) {

        // We expect exactly one user with this email.
        if (matches.size() != 1) {
            return false;
        }

        User user = matches.get(0);

        // Return true only if every account detail is correct.
        return email.equals(user.getEmail())
                && bank.getId().equals(user.getFinancialInstitution().getId())
                && user.getRole() == role
                && user.getStatus() == UserStatus.ACTIVE
                && user.isEmailVerified();
    }


    // Reads a demo password: the account's own variable first,
    // then the shared LIMITGUARD_DEMO_PASSWORD.
    private String resolvePassword(DemoUser demo) {

        String password = System.getenv(demo.passwordVariable());

        if (password == null || password.isBlank()) {
            password = System.getenv(SHARED_PASSWORD_VARIABLE);
        }

        // BCrypt supports passwords up to 72 bytes.
        // Check the UTF-8 byte length, not just the character count.
        if (password == null
                || password.isBlank()
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {

            throw new IllegalStateException(
                    "Set " + demo.passwordVariable() + " or " + SHARED_PASSWORD_VARIABLE
                            + " to a non-blank password of at most 72 UTF-8 bytes"
            );
        }

        return password;
    }


    // Stores the details needed to create a new GIB demo user.
    private record DemoUser(
            String firstName,
            String lastName,
            String email,
            UserRole role,
            String passwordVariable
    ) {
    }
}
