package com.example.limitguard.config;

import com.example.limitguard.enums.UserRole;
import com.example.limitguard.model.Counterparty;
import com.example.limitguard.model.CreditLimit;
import com.example.limitguard.model.FinancialInstitution;
import com.example.limitguard.model.User;
import com.example.limitguard.repository.CounterpartyRepository;
import com.example.limitguard.repository.CreditLimitRepository;
import com.example.limitguard.repository.FinancialInstitutionRepository;
import com.example.limitguard.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import com.example.limitguard.enums.CounterpartyStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;

// tells spring that this class contains our demo data configuration
@Configuration
// runs the seeder only when the development profile is active
@Profile("dev")
public class DemoDataSeeder {

    // automatically adds sample institutions, counterparties and users when the application starts
    @Bean
    public CommandLineRunner seedDemoData(
            FinancialInstitutionRepository institutionRepository,
            CounterpartyRepository counterpartyRepository,
            CreditLimitRepository creditLimitRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder) {

        return args -> {

            // stores fictional bank names used for our demonstration
            String[] institutionNames = {
                    "Pearl Gulf Commercial Bank",
                    "Al Noor Investment Bank",
                    "Bahrain Meridian Bank",
                    "Khaleej Capital Bank",
                    "Gulf Horizon Financial Group",
                    "Crescent International Bank"
            };

            // goes through each bank and checks whether it already exists
            for (String name : institutionNames) {

                // prevents creating the same institution every time spring boot restarts
                if (!institutionRepository.existsByName(name)) {
                    FinancialInstitution institution = new FinancialInstitution();
                    institution.setName(name);
                    institutionRepository.save(institution);
                }
            }

            // stores fictional companies from different business sectors
            String[] counterpartyNames = {

                    // logistics and transportation
                    "Gulf Meridian Logistics W.L.L.",
                    "Pearl Coast Shipping Co.",
                    "Falcon Freight Services",
                    "Bahrain Gateway Warehousing",
                    "Crescent Maritime Holdings",

                    // energy and industrial
                    "Al Dana Petrochemicals",
                    "Horizon Energy Services",
                    "Gulf Alloy Industries",
                    "Peninsula Industrial Equipment",
                    "Blue Dune Engineering",

                    // real estate and construction
                    "Seef Landmark Properties",
                    "Marina Crest Developments",
                    "Bahrain Urban Construction",
                    "Pearl Bay Real Estate",
                    "Al Waha Infrastructure",

                    // retail and hospitality
                    "Souq Pearl Retail Group",
                    "Palm Gate Hospitality",
                    "Bahrain Fresh Foods",
                    "Crescent Consumer Products",
                    "Golden Dhow Trading",

                    // technology and professional services
                    "Gulf Nexus Technology",
                    "Cloud Pearl Solutions",
                    "Meridian Digital Services",
                    "Bahrain Data Systems",
                    "Falcon Business Consulting",

                    // healthcare and manufacturing
                    "Al Hayat Medical Supplies",
                    "Pearl Pharma Distribution",
                    "Gulf Precision Manufacturing",
                    "Crescent Healthcare Group",
                    "Bahrain Advanced Packaging"
            };

            // goes through each company and checks whether it already exists
            for (String name : counterpartyNames) {

                // prevents duplicate counterparties when the application restarts
                if (!counterpartyRepository.existsByName(name)) {
                    Counterparty counterparty = new Counterparty();
                    counterparty.setName(name);
                    counterpartyRepository.save(counterparty);
                }
            }

            // stores the demo counterparties that should be frozen
            String[] frozenCounterparties = {
                    "Pearl Coast Shipping Co.",
                    "Horizon Energy Services",
                    "Marina Crest Developments",
                    "Palm Gate Hospitality",
                    "Cloud Pearl Solutions",
                    "Pearl Pharma Distribution"
            };

            // stores the demo counterparties whose relationships are closed
            String[] closedCounterparties = {
                    "Crescent Maritime Holdings",
                    "Blue Dune Engineering",
                    "Golden Dhow Trading",
                    "Bahrain Advanced Packaging"
            };

            // finds each frozen counterparty and updates its status
            for (String name : frozenCounterparties) {

                counterpartyRepository.findAll().stream()
                        .filter(counterparty -> counterparty.getName().equals(name))
                        .findFirst()
                        .ifPresent(counterparty -> {

                            // only changes active demo counterparties so later manual status changes are preserved
                            if (counterparty.getStatus() == CounterpartyStatus.ACTIVE) {
                                counterparty.setStatus(CounterpartyStatus.FROZEN);
                                counterpartyRepository.save(counterparty);
                            }
                        });
            }

            // finds each closed counterparty and updates its status
            for (String name : closedCounterparties) {

                counterpartyRepository.findAll().stream()
                        .filter(counterparty -> counterparty.getName().equals(name))
                        .findFirst()
                        .ifPresent(counterparty -> {

                            // only changes active demo counterparties so later manual status changes are preserved
                            if (counterparty.getStatus() == CounterpartyStatus.ACTIVE) {
                                counterparty.setStatus(CounterpartyStatus.CLOSED);
                                counterpartyRepository.save(counterparty);
                            }
                        });
            }

            // stores five counterparty limits for each demo bank in whole bahraini dinars
            // each row contains the bank, counterparty and maximum credit amount
            String[][] demoLimits = {
                    // pearl gulf commercial bank
                    {"Pearl Gulf Commercial Bank", "Gulf Meridian Logistics W.L.L.", "750000"},
                    {"Pearl Gulf Commercial Bank", "Pearl Coast Shipping Co.", "1250000"},
                    {"Pearl Gulf Commercial Bank", "Falcon Freight Services", "450000"},
                    {"Pearl Gulf Commercial Bank", "Bahrain Gateway Warehousing", "1800000"},
                    {"Pearl Gulf Commercial Bank", "Crescent Maritime Holdings", "900000"},

                    // al noor investment bank
                    {"Al Noor Investment Bank", "Al Dana Petrochemicals", "2500000"},
                    {"Al Noor Investment Bank", "Horizon Energy Services", "1600000"},
                    {"Al Noor Investment Bank", "Gulf Alloy Industries", "950000"},
                    {"Al Noor Investment Bank", "Peninsula Industrial Equipment", "650000"},
                    {"Al Noor Investment Bank", "Blue Dune Engineering", "1100000"},

                    // bahrain meridian bank
                    {"Bahrain Meridian Bank", "Seef Landmark Properties", "3200000"},
                    {"Bahrain Meridian Bank", "Marina Crest Developments", "2400000"},
                    {"Bahrain Meridian Bank", "Bahrain Urban Construction", "1300000"},
                    {"Bahrain Meridian Bank", "Pearl Bay Real Estate", "1750000"},
                    {"Bahrain Meridian Bank", "Al Waha Infrastructure", "850000"},

                    // khaleej capital bank
                    {"Khaleej Capital Bank", "Souq Pearl Retail Group", "650000"},
                    {"Khaleej Capital Bank", "Palm Gate Hospitality", "900000"},
                    {"Khaleej Capital Bank", "Bahrain Fresh Foods", "400000"},
                    {"Khaleej Capital Bank", "Crescent Consumer Products", "1150000"},
                    {"Khaleej Capital Bank", "Golden Dhow Trading", "550000"},

                    // gulf horizon financial group
                    {"Gulf Horizon Financial Group", "Gulf Nexus Technology", "800000"},
                    {"Gulf Horizon Financial Group", "Cloud Pearl Solutions", "600000"},
                    {"Gulf Horizon Financial Group", "Meridian Digital Services", "1200000"},
                    {"Gulf Horizon Financial Group", "Bahrain Data Systems", "500000"},
                    {"Gulf Horizon Financial Group", "Falcon Business Consulting", "350000"},

                    // crescent international bank
                    {"Crescent International Bank", "Al Hayat Medical Supplies", "700000"},
                    {"Crescent International Bank", "Pearl Pharma Distribution", "1450000"},
                    {"Crescent International Bank", "Gulf Precision Manufacturing", "1900000"},
                    {"Crescent International Bank", "Crescent Healthcare Group", "2200000"},
                    {"Crescent International Bank", "Bahrain Advanced Packaging", "750000"}
            };

            // loads saved banks and counterparties to connect each credit limit correctly
            var savedInstitutions = institutionRepository.findAll();
            var savedCounterparties = counterpartyRepository.findAll();

            for (String[] row : demoLimits) {
                String institutionName = row[0];
                String counterpartyName = row[1];
                BigDecimal amount = new BigDecimal(row[2]);

                // finds the bank that owns this credit limit
                FinancialInstitution institution = savedInstitutions.stream()
                        .filter(bank -> bank.getName().equals(institutionName))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException(
                                "Demo institution not found: " + institutionName));

                // finds the company receiving this credit limit
                Counterparty counterparty = savedCounterparties.stream()
                        .filter(company -> company.getName().equals(counterpartyName))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException(
                                "Demo counterparty not found: " + counterpartyName));

                // avoids duplicates and preserves balances changed by later credit requests
                if (creditLimitRepository.existsByFinancialInstitutionIdAndCounterpartyId(
                        institution.getId(), counterparty.getId())) {
                    continue;
                }

                // starts with no exposure; future credit requests update reserved and used amounts
                CreditLimit creditLimit = new CreditLimit();
                creditLimit.setFinancialInstitution(institution);
                creditLimit.setCounterparty(counterparty);
                creditLimit.setLimitAmount(amount);
                creditLimit.setUsedAmount(BigDecimal.ZERO);
                creditLimit.setReservedAmount(BigDecimal.ZERO);
                creditLimitRepository.save(creditLimit);
            }

            // stores five fictional employees for each bank
            // each bank has one admin, two relationship managers and two risk officers
            String[][] employees = {

                    // pearl gulf commercial bank
                    {"Ahmed", "Mansoor", "ADMIN", "Pearl Gulf Commercial Bank"},
                    {"Alia", "Hassan", "RELATIONSHIP_MANAGER", "Pearl Gulf Commercial Bank"},
                    {"Omar", "Al Khalifa", "RELATIONSHIP_MANAGER", "Pearl Gulf Commercial Bank"},
                    {"Fatima", "Al Zayani", "RISK_OFFICER", "Pearl Gulf Commercial Bank"},
                    {"Huda", "Al Khalifa", "RISK_OFFICER", "Pearl Gulf Commercial Bank"},

                    // al noor investment bank
                    {"Rashid", "Al Mahmood", "ADMIN", "Al Noor Investment Bank"},
                    {"Maryam", "Al Haddad", "RELATIONSHIP_MANAGER", "Al Noor Investment Bank"},
                    {"Dana", "Abdullah", "RELATIONSHIP_MANAGER", "Al Noor Investment Bank"},
                    {"Yusuf", "Rahman", "RISK_OFFICER", "Al Noor Investment Bank"},
                    {"Hassan", "Al Sayed", "RISK_OFFICER", "Al Noor Investment Bank"},

                    // bahrain meridian bank
                    {"Nasser", "Al Farsi", "ADMIN", "Bahrain Meridian Bank"},
                    {"Sara", "Mahmood", "RELATIONSHIP_MANAGER", "Bahrain Meridian Bank"},
                    {"Jassim", "Hassan", "RELATIONSHIP_MANAGER", "Bahrain Meridian Bank"},
                    {"Khalid", "Al Ansari", "RISK_OFFICER", "Bahrain Meridian Bank"},
                    {"Mona", "Ibrahim", "RISK_OFFICER", "Bahrain Meridian Bank"},

                    // khaleej capital bank
                    {"Salman", "Al Khalifa", "ADMIN", "Khaleej Capital Bank"},
                    {"Noor", "Al Sayed", "RELATIONSHIP_MANAGER", "Khaleej Capital Bank"},
                    {"Abdulla", "Yusuf", "RELATIONSHIP_MANAGER", "Khaleej Capital Bank"},
                    {"Faisal", "Karim", "RISK_OFFICER", "Khaleej Capital Bank"},
                    {"Reem", "Ahmed", "RISK_OFFICER", "Khaleej Capital Bank"},

                    // gulf horizon financial group
                    {"Mohammed", "Al Nasser", "ADMIN", "Gulf Horizon Financial Group"},
                    {"Layla", "Hassan", "RELATIONSHIP_MANAGER", "Gulf Horizon Financial Group"},
                    {"Zainab", "Ali", "RELATIONSHIP_MANAGER", "Gulf Horizon Financial Group"},
                    {"Zain", "Ali", "RISK_OFFICER", "Gulf Horizon Financial Group"},
                    {"Amal", "Saleh", "RISK_OFFICER", "Gulf Horizon Financial Group"},

                    // crescent international bank
                    {"Tariq", "Al Jaber", "ADMIN", "Crescent International Bank"},
                    {"Aisha", "Mohammed", "RELATIONSHIP_MANAGER", "Crescent International Bank"},
                    {"Hamad", "Yusuf", "RELATIONSHIP_MANAGER", "Crescent International Bank"},
                    {"Lulwa", "Abdullah", "RISK_OFFICER", "Crescent International Bank"},
                    {"Ibrahim", "Saeed", "RISK_OFFICER", "Crescent International Bank"}
            };

            // reads the demo password from an environment variable instead of storing it in github
            String demoPassword = System.getenv("LIMITGUARD_DEMO_PASSWORD");

            // checks whether any demo employee accounts still need to be created
            boolean usersNeedSeeding = false;

            for (int i = 0; i < employees.length; i++) {
                String email = "demo.employee" + (i + 1) + "@example.com";

                if (userRepository.findByEmail(email).isEmpty()) {
                    usersNeedSeeding = true;
                    break;
                }
            }

            // only requires a demo password when new employee accounts must be created
            if (usersNeedSeeding && (demoPassword == null || demoPassword.isBlank())) {
                throw new IllegalStateException(
                        "Set LIMITGUARD_DEMO_PASSWORD before seeding demo users"
                );
            }

            // goes through each employee and creates their account if it does not exist
            for (int i = 0; i < employees.length; i++) {

                String firstName = employees[i][0];
                String lastName = employees[i][1];
                UserRole role = UserRole.valueOf(employees[i][2]);
                String institutionName = employees[i][3];

                // creates a unique fictional email address for each employee
                String email = "demo.employee" + (i + 1) + "@example.com";

                // skips employees that were already created during a previous startup
                if (userRepository.findByEmail(email).isPresent()) {
                    continue;
                }

                // finds the bank that the employee belongs to
                FinancialInstitution institution = institutionRepository.findAll()
                        .stream()
                        .filter(bank -> bank.getName().equals(institutionName))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException(
                                "Demo institution not found: " + institutionName
                        ));

                // creates the new employee account
                User user = new User();
                user.setFirstName(firstName);
                user.setLastName(lastName);
                user.setEmail(email);

                // hashes the password so the database never stores it as plain text
                user.setPassword(passwordEncoder.encode(demoPassword));

                // assigns the employee's role and bank
                user.setRole(role);
                user.setFinancialInstitution(institution);

                // marks fictional demo accounts as verified so they can log in
                user.setEmailVerified(true);

                // saves the employee account in postgresql
                userRepository.save(user);
            }
        };
    }
}
