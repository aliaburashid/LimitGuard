package com.example.authservice;

import com.example.authservice.enums.FinancialInstitutionStatus;
import com.example.authservice.model.FinancialInstitution;
import com.example.authservice.repository.FinancialInstitutionRepository;
import com.example.authservice.service.EmailService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:auth_directory_regression;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never",
        "jwt-secret=auth-regression-test-only-signing-key",
        "jwt-expiration-ms=60000",
        "internal-service-key=auth-regression-test-only-key"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class FinancialInstitutionDirectoryTest {

    @MockitoBean
    private EmailService emailService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FinancialInstitutionRepository financialInstitutionRepository;

    private void createInstitution(String name, FinancialInstitutionStatus status) {
        FinancialInstitution institution = new FinancialInstitution();
        institution.setName(name);
        institution.setStatus(status);
        financialInstitutionRepository.saveAndFlush(institution);
    }

    @Test
    void returnsOnlyActiveInstitutionIdsAndNamesInNameOrder() throws Exception {
        createInstitution("Zulu Test Bank", FinancialInstitutionStatus.ACTIVE);
        createInstitution("Alpha Test Bank", FinancialInstitutionStatus.ACTIVE);
        createInstitution("Hidden Test Bank", FinancialInstitutionStatus.INACTIVE);

        mockMvc.perform(get("/api/financial-institutions/directory"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Alpha Test Bank"))
                .andExpect(jsonPath("$[1].name").value("Zulu Test Bank"))
                .andExpect(jsonPath("$[0].id").isNumber())
                .andExpect(jsonPath("$[0].status").doesNotExist())
                .andExpect(jsonPath("$[0].createdAt").doesNotExist())
                .andExpect(jsonPath("$[0].updatedAt").doesNotExist());
    }

    @Test
    void returnsAnEmptyArrayWhenNoActiveInstitutionsExist() throws Exception {
        createInstitution("Inactive Test Bank", FinancialInstitutionStatus.INACTIVE);

        mockMvc.perform(get("/api/financial-institutions/directory"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void publicDirectoryDoesNotAcceptAnonymousWrites() throws Exception {
        mockMvc.perform(post("/api/financial-institutions/directory"))
                .andExpect(status().isUnauthorized());
    }
}
