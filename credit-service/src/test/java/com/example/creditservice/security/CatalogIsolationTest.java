package com.example.creditservice.security;

import com.example.creditservice.client.AuthServiceClient;
import com.example.creditservice.dto.AuthContextResponse;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.username=expiry_test",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never",
        "limitguard.notification-relay.enabled=false",
        "limitguard.credit-request-expiry.enabled=false",
        "limitguard.exposure-snapshots.enabled=false",
        "jwt-secret=" + CatalogIsolationTest.KEY,
        "internal-service-key=catalog-isolation-test-only-key"
})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@EnabledIfEnvironmentVariable(named = "CREDIT_EXPIRY_TEST_JDBC_URL", matches = ".+")
@Transactional
class CatalogIsolationTest {
    static final String KEY = "catalog-isolation-test-only-signing-key";
    private static final String PROFILE = """
        {"returnOnAssets":5,"profitMargin":8,"fixedAssetTurnover":1.2,
         "debtToTotalCapitalPercent":40,"interestCoverage":3,"totalDebtToEbitda":2,
         "financialYearEnd":"2025-12-31"}
        """;

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @MockitoBean AuthServiceClient auth;

    long citiCompany, gibCompany, sharedCompany, gibFacility, citiSharedFacility, gibSharedFacility;
    long citiGroup, gibGroup, sharedGroup, gibMembership;
    String citiRm, citiRo, gibRm, gibRo;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        String url = System.getenv("CREDIT_EXPIRY_TEST_JDBC_URL");
        if (!url.matches("jdbc:postgresql://127[.]0[.]0[.]1:[0-9]{1,5}/limitguard_expiry_test")
                || url.contains(":5432/")) {
            throw new IllegalArgumentException("Catalog tests require dedicated loopback PostgreSQL");
        }
        registry.add("spring.datasource.url", () -> url);
    }

    @BeforeEach
    void fixtures() {
        citiRm = token(101, 1L, "RELATIONSHIP_MANAGER");
        citiRo = token(102, 1L, "RISK_OFFICER");
        gibRm = token(103, 2L, "RELATIONSHIP_MANAGER");
        gibRo = token(104, 2L, "RISK_OFFICER");
        citiCompany = company("Citi Workshop");
        gibCompany = company("GIB Workshop");
        sharedCompany = company("Shared Workshop");
        facility(citiCompany, 1);
        gibFacility = facility(gibCompany, 2);
        citiSharedFacility = facility(sharedCompany, 1);
        gibSharedFacility = facility(sharedCompany, 2);
        citiGroup = group("Citi Portfolio");
        gibGroup = group("GIB Portfolio");
        sharedGroup = group("Shared Portfolio");
        membership(citiGroup, citiCompany);
        gibMembership = membership(gibGroup, gibCompany);
        membership(sharedGroup, sharedCompany);
        long gibSubsidiary = company("GIB Services");
        facility(gibSubsidiary, 2);
        membership(sharedGroup, gibSubsidiary);
    }

    @Test
    void searchAndDirectReadsAreScopedButSharedFacilitiesRemainSeparate() throws Exception {
        mvc.perform(get("/api/counterparties?name=Workshop&status=ACTIVE&size=1&sort=name,asc").header("Authorization", citiRm))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].id").value(citiCompany));
        mvc.perform(get("/api/counterparties?name=Workshop&status=ACTIVE&size=1&sort=name,asc").header("Authorization", gibRm))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].id").value(gibCompany));
        for (String token : new String[]{citiRm, citiRo, gibRm, gibRo}) {
            mvc.perform(get("/api/counterparties/{id}", sharedCompany).header("Authorization", token))
                    .andExpect(status().isOk());
        }
        mvc.perform(get("/api/counterparties/{id}", gibCompany).header("Authorization", citiRm))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/counterparties/{id}", citiCompany).header("Authorization", gibRo))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/credit-limits/{id}/exposure", citiSharedFacility).header("Authorization", citiRm))
                .andExpect(status().isOk());
        mvc.perform(get("/api/credit-limits/{id}/exposure", gibSharedFacility).header("Authorization", gibRm))
                .andExpect(status().isOk());
        mvc.perform(get("/api/credit-limits/{id}/exposure", gibSharedFacility).header("Authorization", citiRm))
                .andExpect(status().isForbidden());
    }

    @Test
    void sharedGroupShowsOnlyVisibleMembersAndCannotBeChangedByOneBank() throws Exception {
        mvc.perform(get("/api/corporate-groups?size=1").header("Authorization", citiRm))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/corporate-groups/{id}", citiGroup).header("Authorization", gibRm))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/corporate-group-memberships/{id}", gibMembership).header("Authorization", citiRm))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/corporate-group-memberships/group/{id}", sharedGroup).header("Authorization", citiRm))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/corporate-group-memberships/group/{id}", sharedGroup).header("Authorization", gibRm))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(delete("/api/corporate-groups/{id}", sharedGroup).header("Authorization", gibRo))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/corporate-groups/{id}/risk-assessment", gibGroup).header("Authorization", citiRo))
                .andExpect(status().isNotFound());
    }

    @Test
    void writesRejectForeignAndSharedCatalogIdsButAllowAnOwnedProfile() throws Exception {
        mvc.perform(put("/api/counterparties/{id}/financial-profile", gibCompany)
                        .header("Authorization", gibRo).contentType("application/json").content(PROFILE))
                .andExpect(status().isOk());
        mvc.perform(get("/api/counterparties/{id}/financial-profile", gibCompany).header("Authorization", gibRm))
                .andExpect(status().isOk());
        mvc.perform(get("/api/counterparties/{id}/financial-profile", gibCompany).header("Authorization", citiRm))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/counterparties/{id}/financial-profile", gibCompany)
                        .header("Authorization", citiRo).contentType("application/json").content(PROFILE))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/counterparties/{id}/financial-profile", sharedCompany)
                        .header("Authorization", gibRo).contentType("application/json").content(PROFILE))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/credit-limits").header("Authorization", citiRo).contentType("application/json")
                        .content("{\"counterpartyId\":" + gibCompany + ",\"limitAmount\":1000}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/counterparties/{id}/risk-prediction", gibCompany).header("Authorization", citiRo))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/credit-limits/{id}/exposure", gibFacility).header("Authorization", gibRo))
                .andExpect(status().isOk());
    }

    @Test
    void newCompanyCanReceiveItsFirstFacilityOnlyFromItsCreatingBank() throws Exception {
        mvc.perform(post("/api/counterparties").header("Authorization", citiRo)
                        .contentType("application/json").content("{\"name\":\"Citi New Workshop\"}"))
                .andExpect(status().isCreated());
        Long id = db.queryForObject("select id from counterparties where name = 'Citi New Workshop'", Long.class);
        mvc.perform(get("/api/counterparties/{id}", id).header("Authorization", citiRm))
                .andExpect(status().isOk());
        mvc.perform(get("/api/counterparties/{id}", id).header("Authorization", gibRm))
                .andExpect(status().isNotFound());
        String facility = "{\"counterpartyId\":" + id + ",\"limitAmount\":1000}";
        mvc.perform(post("/api/credit-limits").header("Authorization", gibRo)
                        .contentType("application/json").content(facility))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/credit-limits").header("Authorization", citiRo)
                        .contentType("application/json").content(facility))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/credit-limits/{id}/exposure", db.queryForObject(
                        "select id from credit_limits where counterparty_id = ? and financial_institution_id = 1",
                        Long.class, id)).header("Authorization", citiRm))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usedAmount").value(0))
                .andExpect(jsonPath("$.reservedAmount").value(0));
        mvc.perform(post("/api/credit-limits").header("Authorization", citiRo)
                        .contentType("application/json").content(facility))
                .andExpect(status().isConflict());
        mvc.perform(patch("/api/counterparties/{id}/status", sharedCompany).header("Authorization", citiRo)
                        .contentType("application/json").content("{\"status\":\"FROZEN\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void missingFacilityExposureReturnsNotFound() throws Exception {
        mvc.perform(get("/api/credit-limits/{id}/exposure", Long.MAX_VALUE).header("Authorization", citiRm))
                .andExpect(status().isNotFound());
    }

    @Test
    void facilityDirectoryPaginatesOnlyTheAuthenticatedBank() throws Exception {
        Long firstFacility = db.queryForObject("""
                update credit_limits set limit_amount = 500, used_amount = 200, reserved_amount = 50
                where counterparty_id = ? and financial_institution_id = 1 returning id
                """, Long.class, citiCompany);
        mvc.perform(get("/api/credit-limits").param("size", "1").param("institutionId", "2")
                        .header("Authorization", citiRm))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].name").value("Citi Workshop"))
                .andExpect(jsonPath("$.content[0].exposure.creditLimitId").value(firstFacility.intValue()))
                .andExpect(jsonPath("$.content[0].exposure.availableHeadroom").value(250.0));
        mvc.perform(get("/api/credit-limits").param("size", "1").param("page", "1")
                        .header("Authorization", citiRm))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].exposure.creditLimitId").value((int) citiSharedFacility));
    }

    @Test
    void facilityDirectoryRejectsAuthContextWithoutAnInstitution() throws Exception {
        String unscoped = token(105, null, "RELATIONSHIP_MANAGER");
        mvc.perform(get("/api/credit-limits").header("Authorization", unscoped))
                .andExpect(status().isUnauthorized());
    }

    private String token(long uid, Long bank, String role) {
        String jwt = Jwts.builder().subject("catalog-" + uid + "@example.test")
                .expiration(Date.from(Instant.now().plusSeconds(300)))
                .signWith(Keys.hmacShaKeyFor(KEY.getBytes(StandardCharsets.UTF_8))).compact();
        when(auth.getAuthContext(jwt)).thenReturn(new AuthContextResponse(uid, role, "ACTIVE", bank));
        return "Bearer " + jwt;
    }

    private long company(String name) {
        return db.queryForObject("insert into counterparties(name,status,created_at,updated_at) values (?,'ACTIVE',now(),now()) returning id", Long.class, name);
    }

    private long facility(long company, long bank) {
        return db.queryForObject("""
            insert into credit_limits(counterparty_id,financial_institution_id,limit_amount,used_amount,reserved_amount,created_at,updated_at)
            values (?,?,1000,0,0,now(),now()) returning id
            """, Long.class, company, bank);
    }

    private long group(String name) {
        return db.queryForObject("insert into corporate_groups(name,created_at,updated_at) values (?,now(),now()) returning id", Long.class, name);
    }

    private long membership(long group, long company) {
        return db.queryForObject("""
            insert into corporate_group_memberships(corporate_group_id,counterparty_id) values (?,?) returning id
            """, Long.class, group, company);
    }
}
