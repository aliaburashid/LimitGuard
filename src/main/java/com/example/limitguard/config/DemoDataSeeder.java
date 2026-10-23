package com.example.limitguard.config;

import com.example.limitguard.enums.CounterpartyStatus;
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
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;

@Configuration
@Profile("dev")
public class DemoDataSeeder {

    // adds demo data when the application starts
    @Bean
    public CommandLineRunner seedDemoData(
            FinancialInstitutionRepository institutionRepository,
            CounterpartyRepository counterpartyRepository,
            CreditLimitRepository creditLimitRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder) {

        return args -> {

            // stores the banks for our demo
            String[] institutionNames = {
                    "Citi Bank",
                    "Pearl Gulf Commercial Bank",
                    "Al Noor Investment Bank",
                    "Bahrain Meridian Bank",
                    "Khaleej Capital Bank",
                    "Gulf Horizon Financial Group",
                    "Crescent International Bank"
            };

            // creates each bank if it does not exist
            for (String name : institutionNames) {
                if (!institutionRepository.existsByName(name)) {
                    FinancialInstitution institution = new FinancialInstitution();
                    institution.setName(name);
                    institutionRepository.save(institution);
                }
            }

            // stores fictional company names
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

            // creates each company if it does not exist
            for (String name : counterpartyNames) {
                if (!counterpartyRepository.existsByName(name)) {
                    Counterparty counterparty = new Counterparty();
                    counterparty.setName(name);
                    counterparty.setStatus(CounterpartyStatus.ACTIVE);
                    counterpartyRepository.save(counterparty);
                }
            }

            // stores companies that should be frozen
            String[] frozenCounterparties = {
                    "Pearl Coast Shipping Co.",
                    "Horizon Energy Services",
                    "Marina Crest Developments",
                    "Palm Gate Hospitality",
                    "Cloud Pearl Solutions",
                    "Pearl Pharma Distribution"
            };

            // stores companies that should be closed
            String[] closedCounterparties = {
                    "Crescent Maritime Holdings",
                    "Blue Dune Engineering",
                    "Golden Dhow Trading",
                    "Bahrain Advanced Packaging"
            };

            // freezes active demo companies
            for (String name : frozenCounterparties) {
                counterpartyRepository.findAll().stream()
                        .filter(counterparty -> counterparty.getName().equals(name))
                        .findFirst()
                        .ifPresent(counterparty -> {
                            if (counterparty.getStatus() == CounterpartyStatus.ACTIVE) {
                                counterparty.setStatus(CounterpartyStatus.FROZEN);
                                counterpartyRepository.save(counterparty);
                            }
                        });
            }

            // closes active demo companies
            for (String name : closedCounterparties) {
                counterpartyRepository.findAll().stream()
                        .filter(counterparty -> counterparty.getName().equals(name))
                        .findFirst()
                        .ifPresent(counterparty -> {
                            if (counterparty.getStatus() == CounterpartyStatus.ACTIVE) {
                                counterparty.setStatus(CounterpartyStatus.CLOSED);
                                counterpartyRepository.save(counterparty);
                            }
                        });
            }

            // stores the bank, company and credit limit amount
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
                    {"Crescent International Bank", "Bahrain Advanced Packaging", "750000"},

                    // extra limits for sorting and filtering
                    {"Pearl Gulf Commercial Bank", "Al Dana Petrochemicals", "2500000"},
                    {"Pearl Gulf Commercial Bank", "Cloud Pearl Solutions", "600000"},
                    {"Pearl Gulf Commercial Bank", "Blue Dune Engineering", "1100000"},
                    {"Pearl Gulf Commercial Bank", "Bahrain Fresh Foods", "400000"},
                    {"Pearl Gulf Commercial Bank", "Seef Landmark Properties", "3200000"},
                    {"Pearl Gulf Commercial Bank", "Gulf Nexus Technology", "800000"},

                    {"Al Noor Investment Bank", "Pearl Coast Shipping Co.", "1250000"},
                    {"Al Noor Investment Bank", "Golden Dhow Trading", "550000"},
                    {"Al Noor Investment Bank", "Bahrain Data Systems", "500000"},
                    {"Al Noor Investment Bank", "Seef Landmark Properties", "3200000"},
                    {"Al Noor Investment Bank", "Gulf Meridian Logistics W.L.L.", "750000"},
                    {"Al Noor Investment Bank", "Gulf Nexus Technology", "800000"},

                    {"Bahrain Meridian Bank", "Cloud Pearl Solutions", "600000"},
                    {"Bahrain Meridian Bank", "Golden Dhow Trading", "550000"},
                    {"Bahrain Meridian Bank", "Al Dana Petrochemicals", "2500000"},
                    {"Bahrain Meridian Bank", "Bahrain Fresh Foods", "400000"},
                    {"Bahrain Meridian Bank", "Gulf Nexus Technology", "800000"},
                    {"Bahrain Meridian Bank", "Gulf Meridian Logistics W.L.L.", "750000"},

                    {"Khaleej Capital Bank", "Horizon Energy Services", "1600000"},
                    {"Khaleej Capital Bank", "Blue Dune Engineering", "1100000"},
                    {"Khaleej Capital Bank", "Gulf Nexus Technology", "800000"},
                    {"Khaleej Capital Bank", "Seef Landmark Properties", "3200000"},
                    {"Khaleej Capital Bank", "Gulf Meridian Logistics W.L.L.", "750000"},
                    {"Khaleej Capital Bank", "Al Dana Petrochemicals", "2500000"},

                    {"Gulf Horizon Financial Group", "Pearl Coast Shipping Co.", "1250000"},
                    {"Gulf Horizon Financial Group", "Crescent Maritime Holdings", "900000"},
                    {"Gulf Horizon Financial Group", "Al Dana Petrochemicals", "2500000"},
                    {"Gulf Horizon Financial Group", "Bahrain Fresh Foods", "400000"},
                    {"Gulf Horizon Financial Group", "Seef Landmark Properties", "3200000"},
                    {"Gulf Horizon Financial Group", "Gulf Meridian Logistics W.L.L.", "750000"},

                    {"Crescent International Bank", "Marina Crest Developments", "2400000"},
                    {"Crescent International Bank", "Golden Dhow Trading", "550000"},
                    {"Crescent International Bank", "Gulf Nexus Technology", "800000"},
                    {"Crescent International Bank", "Bahrain Fresh Foods", "400000"},
                    {"Crescent International Bank", "Seef Landmark Properties", "3200000"},
                    {"Crescent International Bank", "Gulf Meridian Logistics W.L.L.", "750000"},

                    // citi credit limits
                    {"Citi", "Al Dana Petrochemicals", "2500000"},
                    {"Citi", "Bahrain Fresh Foods", "400000"},
                    {"Citi", "Crescent Healthcare Group", "2200000"},
                    {"Citi", "Golden Dhow Trading", "550000"},
                    {"Citi", "Gulf Meridian Logistics W.L.L.", "750000"},
                    {"Citi", "Gulf Nexus Technology", "800000"},
                    {"Citi", "Pearl Coast Shipping Co.", "1250000"},
                    {"Citi", "Seef Landmark Properties", "3200000"},
                    {"Citi", "Horizon Energy Services", "1600000"},
                    {"Citi", "Crescent Maritime Holdings", "900000"}
            };

            // gets all the saved banks and companies
            var savedInstitutions = institutionRepository.findAll();
            var savedCounterparties = counterpartyRepository.findAll();

            // creates credit limits for each bank
            for (String[] row : demoLimits) {
                String institutionName = row[0];
                String counterpartyName = row[1];
                BigDecimal amount = new BigDecimal(row[2]);

                // finds the bank
                FinancialInstitution institution = savedInstitutions.stream()
                        .filter(bank -> bank.getName().equals(institutionName))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException(
                                "Demo institution not found: " + institutionName));

                // finds the company
                Counterparty counterparty = savedCounterparties.stream()
                        .filter(company -> company.getName().equals(counterpartyName))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException(
                                "Demo counterparty not found: " + counterpartyName));

                // skips credit limits that already exist
                if (creditLimitRepository.existsByFinancialInstitutionIdAndCounterpartyId(
                        institution.getId(), counterparty.getId())) {
                    continue;
                }

                // creates a credit limit with no exposure
                CreditLimit creditLimit = new CreditLimit();
                creditLimit.setFinancialInstitution(institution);
                creditLimit.setCounterparty(counterparty);
                creditLimit.setLimitAmount(amount);
                creditLimit.setUsedAmount(BigDecimal.ZERO);
                creditLimit.setReservedAmount(BigDecimal.ZERO);
                creditLimitRepository.save(creditLimit);
            }

            // stores five employees for each bank
            String[][] employees = {

                    // citi
                    {"Alia", "Burashid", "RELATIONSHIP_MANAGER", "Citi", "aliaburashid@hotmail.com"},
                    {"Sara", "Al Haddad", "RELATIONSHIP_MANAGER", "Citi", "sara.alhaddad@example.com"},
                    {"Ahmed", "Al Mansoor", "RISK_OFFICER", "Citi", "burashid993@gmail.com"},
                    {"Fatima", "Al Zayani", "RISK_OFFICER", "Citi", "fatima.alzayani@example.com"},
                    {"Khalid", "Al Mahmood", "ADMIN", "Citi", "admin@limitguard.com"},

                    // pearl gulf commercial bank
                    {"Ahmed", "Mansoor", "ADMIN", "Pearl Gulf Commercial Bank", "ahmed.mansoor.pearl@example.com"},
                    {"Alia", "Hassan", "RELATIONSHIP_MANAGER", "Pearl Gulf Commercial Bank", "alia.hassan.pearl@example.com"},
                    {"Omar", "Al Mahmood", "RELATIONSHIP_MANAGER", "Pearl Gulf Commercial Bank", "omar.almahmood.pearl@example.com"},
                    {"Fatima", "Al Zayani", "RISK_OFFICER", "Pearl Gulf Commercial Bank", "fatima.alzayani.pearl@example.com"},
                    {"Huda", "Al Ansari", "RISK_OFFICER", "Pearl Gulf Commercial Bank", "huda.alansari.pearl@example.com"},

                    // al noor investment bank
                    {"Rashid", "Al Mahmood", "ADMIN", "Al Noor Investment Bank", "rashid.almahmood.al@example.com"},
                    {"Maryam", "Al Haddad", "RELATIONSHIP_MANAGER", "Al Noor Investment Bank", "maryam.alhaddad.al@example.com"},
                    {"Dana", "Abdullah", "RELATIONSHIP_MANAGER", "Al Noor Investment Bank", "dana.abdullah.al@example.com"},
                    {"Yusuf", "Rahman", "RISK_OFFICER", "Al Noor Investment Bank", "yusuf.rahman.al@example.com"},
                    {"Hassan", "Al Sayed", "RISK_OFFICER", "Al Noor Investment Bank", "hassan.alsayed.al@example.com"},

                    // bahrain meridian bank
                    {"Nasser", "Al Farsi", "ADMIN", "Bahrain Meridian Bank", "nasser.alfarsi.bahrain@example.com"},
                    {"Sara", "Mahmood", "RELATIONSHIP_MANAGER", "Bahrain Meridian Bank", "sara.mahmood.bahrain@example.com"},
                    {"Jassim", "Hassan", "RELATIONSHIP_MANAGER", "Bahrain Meridian Bank", "jassim.hassan.bahrain@example.com"},
                    {"Khalid", "Al Ansari", "RISK_OFFICER", "Bahrain Meridian Bank", "khalid.alansari.bahrain@example.com"},
                    {"Mona", "Ibrahim", "RISK_OFFICER", "Bahrain Meridian Bank", "mona.ibrahim.bahrain@example.com"},

                    // khaleej capital bank
                    {"Salman", "Al Hashimi", "ADMIN", "Khaleej Capital Bank", "salman.alhashimi.khaleej@example.com"},
                    {"Noor", "Al Sayed", "RELATIONSHIP_MANAGER", "Khaleej Capital Bank", "noor.alsayed.khaleej@example.com"},
                    {"Abdulla", "Yusuf", "RELATIONSHIP_MANAGER", "Khaleej Capital Bank", "abdulla.yusuf.khaleej@example.com"},
                    {"Faisal", "Karim", "RISK_OFFICER", "Khaleej Capital Bank", "faisal.karim.khaleej@example.com"},
                    {"Reem", "Ahmed", "RISK_OFFICER", "Khaleej Capital Bank", "reem.ahmed.khaleej@example.com"},

                    // gulf horizon financial group
                    {"Mohammed", "Al Nasser", "ADMIN", "Gulf Horizon Financial Group", "mohammed.alnasser.gulf@example.com"},
                    {"Layla", "Hassan", "RELATIONSHIP_MANAGER", "Gulf Horizon Financial Group", "layla.hassan.gulf@example.com"},
                    {"Zainab", "Ali", "RELATIONSHIP_MANAGER", "Gulf Horizon Financial Group", "zainab.ali.gulf@example.com"},
                    {"Zain", "Ali", "RISK_OFFICER", "Gulf Horizon Financial Group", "zain.ali.gulf@example.com"},
                    {"Amal", "Saleh", "RISK_OFFICER", "Gulf Horizon Financial Group", "amal.saleh.gulf@example.com"},

                    // crescent international bank
                    {"Tariq", "Al Jaber", "ADMIN", "Crescent International Bank", "tariq.aljaber.crescent@example.com"},
                    {"Aisha", "Mohammed", "RELATIONSHIP_MANAGER", "Crescent International Bank", "aisha.mohammed.crescent@example.com"},
                    {"Hamad", "Yusuf", "RELATIONSHIP_MANAGER", "Crescent International Bank", "hamad.yusuf.crescent@example.com"},
                    {"Lulwa", "Abdullah", "RISK_OFFICER", "Crescent International Bank", "lulwa.abdullah.crescent@example.com"},
                    {"Ibrahim", "Saeed", "RISK_OFFICER", "Crescent International Bank", "ibrahim.saeed.crescent@example.com"}
            };

            // gets the demo password from the environment
            String demoPassword = System.getenv("LIMITGUARD_DEMO_PASSWORD");

            // checks if any demo users are missing
            boolean usersNeedSeeding = false;

            for (String[] employee : employees) {
                String email = employee[4];

                if (userRepository.findByEmail(email).isEmpty()) {
                    usersNeedSeeding = true;
                    break;
                }
            }

            // requires a password only when creating new users
            if (usersNeedSeeding && (demoPassword == null || demoPassword.isBlank())) {
                throw new IllegalStateException(
                        "Set LIMITGUARD_DEMO_PASSWORD before seeding demo users"
                );
            }

            // creates the demo employees
            for (String[] employee : employees) {
                String firstName = employee[0];
                String lastName = employee[1];
                UserRole role = UserRole.valueOf(employee[2]);
                String institutionName = employee[3];
                String email = employee[4];

                // skips users that already exist
                if (userRepository.findByEmail(email).isPresent()) {
                    continue;
                }

                // finds the employee's bank
                FinancialInstitution institution = savedInstitutions.stream()
                        .filter(bank -> bank.getName().equals(institutionName))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException(
                                "Demo institution not found: " + institutionName));

                // creates the user
                User user = new User();
                user.setFirstName(firstName);
                user.setLastName(lastName);
                user.setEmail(email);
                user.setPassword(passwordEncoder.encode(demoPassword));
                user.setRole(role);
                user.setFinancialInstitution(institution);
                user.setEmailVerified(true);

                // saves the user
                userRepository.save(user);
            }
        };
    }
}
