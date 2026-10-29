package com.example.limitguard.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.example.limitguard.dto.MlRiskPredictionResponse;
import com.example.limitguard.model.Counterparty;
import com.example.limitguard.model.CorporateGroupMembership;
import com.example.limitguard.model.CreditLimit;
import com.example.limitguard.repository.CorporateGroupMembershipRepository;
import com.example.limitguard.repository.CorporateGroupRepository;
import com.example.limitguard.repository.CreditLimitRepository;
import com.example.limitguard.security.InstitutionAccessService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ParallelRiskAssessmentServiceTest {
    @Mock CorporateGroupMembershipRepository membershipRepository;
    @Mock CorporateGroupRepository corporateGroupRepository;
    @Mock CreditLimitRepository creditLimitRepository;
    @Mock InstitutionAccessService institutionAccessService;
    @Mock MlRiskPredictionService mlRiskPredictionService;
    @InjectMocks ParallelRiskAssessmentService service;
    private final List<String> logs = new CopyOnWriteArrayList<>();
    private Logger logger;
    private AppenderBase<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(ParallelRiskAssessmentService.class);
        appender = new AppenderBase<>() {
            @Override protected void append(ILoggingEvent event) { logs.add(event.getFormattedMessage()); }
        };
        appender.start();
        logger.addAppender(appender);
        when(corporateGroupRepository.existsById(3L)).thenReturn(true);
        when(institutionAccessService.getCurrentInstitutionId()).thenReturn(5L);
        List<CorporateGroupMembership> memberships = new ArrayList<>();
        List<CreditLimit> limits = new ArrayList<>();
        for (long id = 3; id <= 6; id++) {
            Counterparty c = new Counterparty(); c.setId(id); c.setName("Company " + id);
            CorporateGroupMembership membership = new CorporateGroupMembership(); membership.setCounterparty(c);
            memberships.add(membership);
            CreditLimit limit = new CreditLimit(); limit.setCounterparty(c); limits.add(limit);
        }
        when(membershipRepository.findByCorporateGroupId(3L)).thenReturn(memberships);
        when(creditLimitRepository.findByFinancialInstitutionIdAndCounterpartyIdIn(eq(5L), anyList())).thenReturn(limits);
    }

    @AfterEach
    void detachLogger() { logger.detachAppender(appender); appender.stop(); }

    private MlRiskPredictionResponse prediction() {
        MlRiskPredictionResponse result = new MlRiskPredictionResponse();
        result.setProbability(new BigDecimal("0.02")); result.setRiskLevel("LOW_RISK"); result.setModelVersion("test");
        return result;
    }

    @Test
    void fourWorkersOverlapAndLogStartsCompletionsAndTotalElapsedTime() {
        CountDownLatch started = new CountDownLatch(4);
        Set<String> threads = ConcurrentHashMap.newKeySet();
        when(mlRiskPredictionService.predictAuthorizedRisk(anyLong())).thenAnswer(invocation -> {
            threads.add(Thread.currentThread().getName()); started.countDown();
            assertTrue(started.await(3, TimeUnit.SECONDS), "Four independent tasks must overlap");
            return prediction();
        });
        var results = service.assessGroup(3L);
        assertEquals(4, results.size());
        assertTrue(results.stream().allMatch(r -> r.isSuccessful()));
        assertEquals(4, threads.size());
        assertEquals(4, logs.stream().filter(l -> l.startsWith("ML WORKER START")).count());
        assertEquals(4, logs.stream().filter(l -> l.startsWith("ML WORKER COMPLETE")).count());
        assertTrue(logs.stream().anyMatch(l -> l.startsWith("ML BATCH START")));
        assertTrue(logs.stream().anyMatch(l -> l.startsWith("ML BATCH COMPLETE") && l.contains("Total elapsed ms=") && l.contains("Outcome=COMPLETED")));
    }

    @Test
    void anIndividualFailureIsLoggedWithoutDiscardingOtherResults() {
        when(mlRiskPredictionService.predictAuthorizedRisk(anyLong())).thenAnswer(invocation -> {
            if ((Long) invocation.getArgument(0) == 5L) throw new IllegalStateException("Unavailable model");
            return prediction();
        });
        var results = service.assessGroup(3L);
        assertEquals(3, results.stream().filter(r -> r.isSuccessful()).count());
        assertEquals(1, results.stream().filter(r -> !r.isSuccessful()).count());
        assertEquals(1, logs.stream().filter(l -> l.startsWith("ML WORKER FAILED") && l.contains("Company ID=5") && l.contains("Thread=")).count());
        assertTrue(logs.stream().anyMatch(l -> l.startsWith("ML BATCH COMPLETE") && l.contains("Total elapsed ms=")));
    }
}
