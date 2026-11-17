package com.example.creditservice.bdd.expiry;

import com.example.creditservice.CreditServiceApplication;
import com.example.creditservice.service.CreditRequestKafkaPublisher;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.postgresql.PostgreSQLContainer;

@CucumberContextConfiguration
@SpringBootTest(classes = {CreditServiceApplication.class, ExpiryCucumberConfiguration.Database.class},
        properties = {
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.sql.init.mode=never",
                "limitguard.credit-request-expiry.enabled=true",
                "limitguard.notification-relay.enabled=false",
                "limitguard.exposure-snapshots.enabled=false",
                "spring.autoconfigure.exclude=org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration,"
                        + "org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration",
                "jwt-secret=credit-expiry-bdd-test-only-signing-key",
                "internal-service-key=credit-expiry-bdd-test-only-key"
        })
@DirtiesContext
public class ExpiryCucumberConfiguration {

    @MockitoBean
    private CreditRequestKafkaPublisher kafkaPublisher;

    @TestConfiguration(proxyBeanMethods = false)
    static class Database {

        @Bean
        @ServiceConnection
        PostgreSQLContainer expiryDatabase() {
            return new PostgreSQLContainer("postgres:18.6")
                    .withDatabaseName("limitguard_expiry_bdd");
        }
    }
}
