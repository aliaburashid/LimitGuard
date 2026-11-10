package com.example.limitguard.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// runs with isolated test settings and no provider credentials
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SsoEndpointTest {
    @Autowired
    private MockMvc mvc;

    @Test
    void exposesAvailabilityWithoutExposingSecrets() throws Exception {
        mvc.perform(get("/api/auth/sso/providers"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"google\":false,\"microsoft\":false}"));
    }

    @Test
    void cannotExchangeWithoutSuccessfulProviderLogin() throws Exception {
        mvc.perform(post("/api/auth/sso/exchange").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsFormSubmissionAndKeepsBankingApisProtected() throws Exception {
        mvc.perform(post("/api/auth/sso/exchange").contentType(MediaType.APPLICATION_FORM_URLENCODED).content("a=b"))
                .andExpect(status().isUnsupportedMediaType());
        mvc.perform(get("/api/auth/users/profile"))
                .andExpect(status().isUnauthorized());
    }
}
