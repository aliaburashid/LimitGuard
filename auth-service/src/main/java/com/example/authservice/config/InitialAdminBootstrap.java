package com.example.authservice.config;

import com.example.authservice.dto.RegisterRequest;
import com.example.authservice.enums.FinancialInstitutionStatus;
import com.example.authservice.enums.UserRole;
import com.example.authservice.enums.UserStatus;
import com.example.authservice.model.FinancialInstitution;
import com.example.authservice.model.User;
import com.example.authservice.repository.FinancialInstitutionRepository;
import com.example.authservice.repository.UserRepository;
import jakarta.validation.Validator;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

/** Creates the initial administrator only in an empty installation. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(name = "limitguard.bootstrap.enabled", havingValue = "true")
public class InitialAdminBootstrap implements ApplicationRunner {
    private final Environment environment;
    private final UserRepository users;
    private final FinancialInstitutionRepository institutions;
    private final PasswordEncoder encoder;
    private final JdbcTemplate jdbc;
    private final Validator validator;

    public InitialAdminBootstrap(Environment environment, UserRepository users,
            FinancialInstitutionRepository institutions, PasswordEncoder encoder,
            JdbcTemplate jdbc, Validator validator) {
        this.environment = environment;
        this.users = users;
        this.institutions = institutions;
        this.encoder = encoder;
        this.jdbc = jdbc;
        this.validator = validator;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        // Reject mixed initialization modes before either seeder writes data.
        if (environment.getProperty("limitguard.seed.demo.enabled", Boolean.class, false)
                || environment.getProperty("limitguard.seed.gib.enabled", Boolean.class, false)) {
            throw new IllegalStateException("Bootstrap cannot run with demo seeding enabled");
        }

        // Exclusive locks serialize bootstrap attempts and block ordinary concurrent inserts.
        // Counts must be checked after acquiring the locks, in this transaction.
        jdbc.execute("SET LOCAL lock_timeout = '15s'");
        jdbc.execute("LOCK TABLE public.financial_institutions, public.users IN EXCLUSIVE MODE");
        if (institutions.count() != 0 || users.count() != 0) {
            throw new IllegalStateException("Bootstrap requires empty users and financial_institutions tables");
        }

        String institutionName = required("AUTH_BOOTSTRAP_INSTITUTION_NAME").trim();
        if (institutionName.length() > 255) {
            throw new IllegalStateException("Bootstrap institution name exceeds 255 characters");
        }
        // Reuse registration validation without calling its email-producing workflow.
        RegisterRequest details = new RegisterRequest();
        details.setFirstName(required("AUTH_BOOTSTRAP_ADMIN_FIRST_NAME").trim());
        details.setLastName(required("AUTH_BOOTSTRAP_ADMIN_LAST_NAME").trim());
        details.setEmail(required("AUTH_BOOTSTRAP_ADMIN_EMAIL").trim());
        details.setFinancialInstitutionId(1L); // Validation only; the real ID is generated below.
        details.setPassword(required("AUTH_BOOTSTRAP_ADMIN_PASSWORD"));
        if (!validator.validate(details).isEmpty()
                || details.getPassword().getBytes(StandardCharsets.UTF_8).length > 72
                || details.getEmail().length() > 255
                || details.getFirstName().length() > 255 || details.getLastName().length() > 255) {
            throw new IllegalStateException("Invalid bootstrap credentials; check registration requirements and field lengths");
        }

        FinancialInstitution institution = new FinancialInstitution();
        institution.setName(institutionName);
        institution.setStatus(FinancialInstitutionStatus.ACTIVE);
        institutions.saveAndFlush(institution);

        User admin = new User();
        admin.setFirstName(details.getFirstName());
        admin.setLastName(details.getLastName());
        admin.setEmail(details.getEmail());
        try {
            admin.setPassword(encoder.encode(details.getPassword()));
        } finally {
            // Do not retain plaintext in configuration beans or account entities.
            details.setPassword(null);
        }
        admin.setRole(UserRole.ADMIN);
        admin.setStatus(UserStatus.ACTIVE);
        // The deployment operator establishes the initial administrator's identity.
        admin.setEmailVerified(true);
        admin.setFinancialInstitution(institution);
        users.saveAndFlush(admin);
    }

    private String required(String name) {
        String value = environment.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required bootstrap setting: " + name);
        }
        return value;
    }
}
