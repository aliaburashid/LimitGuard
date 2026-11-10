package com.example.limitguard;

import com.example.limitguard.enums.FinancialInstitutionStatus;
import com.example.limitguard.model.FinancialInstitution;
import com.example.limitguard.repository.FinancialInstitutionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// uses the isolated H2 test database, not the development banking records
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class FinancialInstitutionDirectoryTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FinancialInstitutionRepository financialInstitutionRepository;

    // creates test-only institutions that are rolled back after each test
    private void createInstitution(String name, FinancialInstitutionStatus status) {
        FinancialInstitution institution = new FinancialInstitution();
        institution.setName(name);
        institution.setStatus(status);
        financialInstitutionRepository.saveAndFlush(institution);
    }

    // users can choose an active institution without being logged in
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
                // the public response must not include internal institution information
                .andExpect(jsonPath("$[0].status").doesNotExist())
                .andExpect(jsonPath("$[0].createdAt").doesNotExist())
                .andExpect(jsonPath("$[0].updatedAt").doesNotExist());
    }

    // an empty directory is a valid response, not an error
    @Test
    void returnsAnEmptyArrayWhenNoActiveInstitutionsExist() throws Exception {
        createInstitution("Inactive Test Bank", FinancialInstitutionStatus.INACTIVE);

        mockMvc.perform(get("/api/financial-institutions/directory"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    // making the directory public must not make institution changes public
    @Test
    void rejectsInstitutionWritesWithoutLogin() throws Exception {
        mockMvc.perform(post("/api/financial-institutions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test Bank\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/financial-institutions/directory"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/financial-institutions"))
                .andExpect(status().isUnauthorized());
    }

    // logged-in relationship managers still cannot create or update institutions
    @Test
    @WithMockUser(roles = "RELATIONSHIP_MANAGER")
    void keepsInstitutionChangesRestrictedToAdmins() throws Exception {
        mockMvc.perform(post("/api/financial-institutions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test Bank\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/financial-institutions/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test Bank\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isForbidden());
    }
}
