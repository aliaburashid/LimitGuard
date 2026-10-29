package com.example.limitguard.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
    void approvedHistoryHasSixteenIndependentPendingRequestsAndValidDates() {
        var seeds = DemoCreditRequestSeeder.approvedRequests(now);
        assertEquals(35, seeds.size());
        assertEquals(35, seeds.stream().map(DemoCreditRequestSeeder.RequestSeed::key).distinct().count());
        assertEquals(Map.of("PENDING_APPROVAL", 16L, "USED", 13L, "RESERVED", 3L,
                        "CANCELLED", 1L, "EXPIRED", 1L, "REJECTED", 1L),
                seeds.stream().collect(Collectors.groupingBy(DemoCreditRequestSeeder.RequestSeed::status, Collectors.counting())));
        for (var seed : seeds) {
            assertFalse(seed.created().isAfter(seed.updated()));
            assertFalse(seed.updated().isAfter(now));
            assertTrue(seed.requester() == 15 || seed.requester() == 58);
            if (seed.status().equals("PENDING_APPROVAL")) {
                assertEquals(0, new BigDecimal("600000").compareTo(seed.amount()));
                assertEquals(15, seed.requester());
                assertNull(seed.expires());
            } else if (seed.expires() != null) {
                assertEquals(seed.created().plusHours(24), seed.expires());
            }
        }
        var expiry = seeds.stream().filter(s -> s.key().endsWith("lifecycle-expiry")).findFirst().orElseThrow();
        assertEquals(now.plusMinutes(5), expiry.expires());
        for (String scenario : DemoCreditRequestSeeder.SCENARIOS) {
            assertEquals(4, seeds.stream().filter(s -> s.key().startsWith(DemoCreditRequestSeeder.PREFIX + scenario + "-")).count());
        }
    }

    @Test
    void openingBalancesDeriveFromHistoryAndAllAviationApprovalsFit() {
        var seeds = DemoCreditRequestSeeder.approvedRequests(now);
        for (var company : DemoDataSeeder.COMPANIES) {
            BigDecimal used = seeds.stream().filter(s -> s.company().equals(company.name()) && s.status().equals("USED"))
                    .map(DemoCreditRequestSeeder.RequestSeed::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal reserved = seeds.stream().filter(s -> s.company().equals(company.name()) && s.status().equals("RESERVED"))
                    .map(DemoCreditRequestSeeder.RequestSeed::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
            assertEquals(0, new BigDecimal(company.used()).compareTo(used));
            assertEquals(0, new BigDecimal(company.reserved()).compareTo(reserved));
            BigDecimal pending = seeds.stream().filter(s -> s.company().equals(company.name()) && s.status().equals("PENDING_APPROVAL"))
                    .map(DemoCreditRequestSeeder.RequestSeed::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
            assertTrue(new BigDecimal(company.limit()).compareTo(used.add(reserved).add(pending)) >= 0);
        }
        assertEquals(0, new BigDecimal("11200000").compareTo(groupHeadroom("Gulf Aviation Holdings", "12000000")));
        assertEquals(0, new BigDecimal("400000").compareTo(groupHeadroom("Pearl Infrastructure Group", "2000000")));
        assertEquals(0, new BigDecimal("800000").compareTo(groupHeadroom("Arabian Energy Group", "2000000")));
    }

    private BigDecimal groupHeadroom(String group, String amount) {
        return new BigDecimal(amount).subtract(DemoDataSeeder.COMPANIES.stream()
                .filter(c -> group.equals(c.group())).map(c -> new BigDecimal(c.used()).add(new BigDecimal(c.reserved())))
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    @Test
    void ordinaryDevContextDoesNotRegisterEitherSeeder() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("dev");
            context.register(DemoDataSeeder.class, DemoCreditRequestSeeder.class);
            context.refresh();
            assertTrue(context.getBeansOfType(DemoDataSeeder.class).isEmpty());
            assertTrue(context.getBeansOfType(DemoCreditRequestSeeder.class).isEmpty());
        }
    }

    @Test
    void historyRequiresTheCallersTransactionAndSeedRollbackCoversCheckedExceptions() throws Exception {
        assertEquals(Propagation.MANDATORY, DemoCreditRequestSeeder.class
                .getMethod("seedCreditRequests", Map.class, LocalDateTime.class)
                .getAnnotation(Transactional.class).propagation());
        assertArrayEquals(new Class<?>[]{Exception.class}, DemoDataSeeder.class.getMethod("run", String[].class)
                .getAnnotation(Transactional.class).rollbackFor());
        assertFalse(DemoDataSeeder.class.getAnnotation(ConditionalOnProperty.class).matchIfMissing());
    }
}
