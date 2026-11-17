package com.example.creditservice.config;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;


// This class manages the demo data seeding for Credit Service.
// It runs only when the "demo" profile is active.
@Component
@Profile("demo & !prod & !production")
@RequiredArgsConstructor
public class DemoDataSeeder implements ApplicationRunner {

    // Handles the demo portfolio data for Citi.
    private final ObjectProvider<CitiPortfolioSeeder> citi;

    // Handles the demo portfolio data for Gulf International Bank.
    private final ObjectProvider<GibPortfolioSeeder> gib;

    // Handles the optional Unit 3 concurrency testing scenarios.
    private final ObjectProvider<Unit3ScenarioSeeder> unit3;


    // This method runs automatically when the application starts.
    @Override
    public void run(ApplicationArguments arguments) {

        // Seed Citi only if its seeder is enabled and available.
        // Its transaction is handled separately.
        citi.ifAvailable(CitiPortfolioSeeder::seedPortfolio);

        // Seed GIB only if its seeder is enabled and available.
        // A GIB failure will not roll back a completed Citi transaction.
        gib.ifAvailable(GibPortfolioSeeder::seedPortfolio);

        // Add Unit 3 test requests only if its seeder is enabled.
        // These requests are used to test simultaneous credit decisions.
        unit3.ifAvailable(Unit3ScenarioSeeder::seedScenarios);
    }
}
