package com.example.limitguard.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class DemoSeedPlanTest {
    private final LocalDateTime now = LocalDateTime.of(2026, 10, 29, 10, 0);

    @Test
    void approvedRecordsContainOnlyTwoHistoricalUsesAndTwoIndependentPendingRequests() {
        var seeds = DemoCreditRequestSeeder.approvedRequests(now);
        assertEquals(4, seeds.size());
        assertEquals(4, seeds.stream().map(DemoCreditRequestSeeder.RequestSeed::key).distinct().count());
        assertEquals(Map.of("USED", 2L, "PENDING_APPROVAL", 2L), seeds.stream()
                .collect(Collectors.groupingBy(DemoCreditRequestSeeder.RequestSeed::status, Collectors.counting())));
        for (var seed : seeds) {
            assertFalse(seed.updated().isBefore(seed.created()));
            assertFalse(seed.updated().isAfter(now));
            if (seed.status().equals("PENDING_APPROVAL")) {
                assertEquals(1, seed.companyId());
                assertEquals(15, seed.requester());
                assertEquals(0, new BigDecimal("600000").compareTo(seed.amount()));
                assertNull(seed.expires());
            } else {
                assertTrue(seed.amount().compareTo(new BigDecimal("500000")) <= 0);
                assertEquals(seed.created().plusHours(24), seed.expires());
            }
        }
    }

    @Test
    void usedHistoryCreatesExactlyEightHundredThousandOfSharedHeadroom() {
        var seeds = DemoCreditRequestSeeder.approvedRequests(now);
        var uses = seeds.stream().filter(s -> s.status().equals("USED")).toList();
        assertEquals(5, uses.get(0).companyId());
        assertEquals(15, uses.get(0).requester());
        assertEquals(6, uses.get(1).companyId());
        assertEquals(58, uses.get(1).requester());
        BigDecimal used = uses.stream().map(DemoCreditRequestSeeder.RequestSeed::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, new BigDecimal("800000").compareTo(new BigDecimal("2000000").subtract(new BigDecimal("675000")).subtract(used)));
        assertEquals(0, new BigDecimal("120000").compareTo(new BigDecimal("2000000").subtract(new BigDecimal("680000")).subtract(new BigDecimal("1200000"))));
    }

    @Test
    void normalDevStartupCannotRunTheSeed() {
        assertFalse(CommandLineRunner.class.isAssignableFrom(DemoDataSeeder.class));
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("dev");
            context.register(DemoDataSeeder.class, DemoCreditRequestSeeder.class);
            context.refresh();
            assertTrue(context.getBeansOfType(DemoDataSeeder.class).isEmpty());
            assertTrue(context.getBeansOfType(DemoCreditRequestSeeder.class).isEmpty());
        }
    }

    @Test
    void historyRequiresParentTransactionAndExplicitSeedRollsBackCheckedFailures() throws Exception {
        assertEquals(Propagation.MANDATORY, DemoCreditRequestSeeder.class
                .getMethod("seedCreditRequests", Map.class, LocalDateTime.class).getAnnotation(Transactional.class).propagation());
        assertArrayEquals(new Class<?>[]{Exception.class}, DemoDataSeeder.class.getMethod("seedApprovedAdjustment")
                .getAnnotation(Transactional.class).rollbackFor());
        assertTrue(DemoDataSeeder.class.getMethod("preflight").getAnnotation(Transactional.class).readOnly());
    }
}
