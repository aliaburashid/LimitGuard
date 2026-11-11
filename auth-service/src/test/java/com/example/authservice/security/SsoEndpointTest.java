package com.example.authservice.security;

import com.example.authservice.controller.SsoController;
import com.example.authservice.enums.FinancialInstitutionStatus;
import com.example.authservice.enums.UserStatus;
import com.example.authservice.model.FinancialInstitution;
import com.example.authservice.model.User;
import com.example.authservice.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.oauth2.client.authentication.OAuth2LoginAuthenticationToken;
import org.springframework.security.oauth2.client.web.OAuth2LoginAuthenticationFilter;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.ObjectMapper;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SsoController.class, properties = {
        "jwt-secret=auth-sso-tests-only-signing-key-32-bytes",
        "jwt-expiration-ms=60000",
        "limitguard.sso.frontend-url=https://limitguard.example.test",
        "limitguard.sso.google.client-id=test-google",
        "limitguard.sso.google.client-secret=test-google-secret",
        "limitguard.sso.google.domains[0]=example.test",
        "limitguard.sso.google.accounts[google-subject]=7",
        "limitguard.sso.microsoft.client-id=test-microsoft",
        "limitguard.sso.microsoft.client-secret=test-microsoft-secret",
        "limitguard.sso.microsoft.tenant-id=11111111-1111-1111-1111-111111111111",
        "limitguard.sso.microsoft.accounts[microsoft-subject]=7"
})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import({SecurityConfiguration.class, SsoFilterConfiguration.class, SsoConfiguration.class,
        SsoLoginService.class, JWTUtils.class})
class SsoEndpointTest {

    private static final String FRONTEND = "https://limitguard.example.test";
    private static final String SSO = "/api/auth/sso";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private FilterChainProxy securityFilters;

    @Autowired
    private JWTUtils jwt;

    @Autowired
    private ObjectMapper json;

    @MockitoBean
    private MyUserDetailsService userDetailsService;

    @MockitoBean
    private UserRepository users;

    @Test
    void exposesOnlyProviderAvailability() throws Exception {
        mvc.perform(get(SSO + "/providers"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"google\":true,\"microsoft\":true}",
                        org.springframework.test.json.JsonCompareMode.STRICT));
    }

    @ParameterizedTest
    @ValueSource(strings = {"google", "microsoft"})
    void linksBrowserCallbackAndExchangesSessionForJwtOnlyOnce(String provider) throws Exception {
        MvcResult authorization = authorize(provider);
        var redirect = UriComponentsBuilder.fromUriString(authorization.getResponse().getRedirectedUrl()).build();
        assertEquals(provider.equals("google") ? "accounts.google.com" : "login.microsoftonline.com",
                redirect.getHost());
        assertEquals(FRONTEND + SSO + "/callback/" + provider,
                decode(redirect.getQueryParams().getFirst("redirect_uri")));

        MockHttpSession authorizationSession = (MockHttpSession) authorization.getRequest().getSession(false);
        User user = activeUser();
        when(users.findById(7L)).thenReturn(Optional.of(user));

        MvcResult callback = callback(provider, authorizationSession,
                decode(redirect.getQueryParams().getFirst("state")), provider + "-subject");
        assertEquals(FRONTEND + "/login#sso=complete", callback.getResponse().getRedirectedUrl());
        assertTrue(authorizationSession.isInvalid());
        MockHttpSession exchangeSession = (MockHttpSession) callback.getRequest().getSession(false);
        mvc.perform(get("/api/auth/users/profile").session(exchangeSession))
                .andExpect(status().isUnauthorized());

        MvcResult exchange = mvc.perform(post(SSO + "/exchange").session(exchangeSession)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn();
        String token = json.readTree(exchange.getResponse().getContentAsString()).get("message").asText();
        assertTrue(jwt.validateJwtToken(token), "Exchange must issue a valid LimitGuard JWT");
        assertEquals(user.getEmail(), jwt.getUserNameFromJwtToken(token));
        assertTrue(exchangeSession.isInvalid());
        mvc.perform(post(SSO + "/exchange").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void clearsSessionWhenProviderDeniesAuthorization() throws Exception {
        MvcResult authorization = authorize("google");
        var query = UriComponentsBuilder.fromUriString(authorization.getResponse().getRedirectedUrl())
                .build().getQueryParams();
        MockHttpSession session = (MockHttpSession) authorization.getRequest().getSession(false);
        mvc.perform(get(SSO + "/callback/google").session(session)
                        .param("error", "access_denied").param("state", decode(query.getFirst("state"))))
                .andExpect(redirectedUrl(FRONTEND + "/login#sso=error"));
        assertTrue(session.isInvalid());
    }

    @Test
    void rejectsSuccessfulProviderAuthenticationForUnlinkedSubject() throws Exception {
        MvcResult authorization = authorize("google");
        var query = UriComponentsBuilder.fromUriString(authorization.getResponse().getRedirectedUrl())
                .build().getQueryParams();
        MockHttpSession session = (MockHttpSession) authorization.getRequest().getSession(false);
        MvcResult callback = callback("google", session, decode(query.getFirst("state")), "unlinked-subject");
        assertEquals(FRONTEND + "/login#sso=error", callback.getResponse().getRedirectedUrl());
        assertTrue(session.isInvalid());
    }

    @Test
    void requiresJsonForExchange() throws Exception {
        mvc.perform(post(SSO + "/exchange").contentType(MediaType.APPLICATION_FORM_URLENCODED).content("a=b"))
                .andExpect(status().isUnsupportedMediaType());
    }

    private MvcResult authorize(String provider) throws Exception {
        return mvc.perform(get(SSO + "/authorize/" + provider))
                .andExpect(status().is3xxRedirection())
                .andReturn();
    }

    private MvcResult callback(String provider, MockHttpSession session, String state, String subject) throws Exception {
        String path = SSO + "/callback/" + provider;
        OAuth2LoginAuthenticationFilter callbackFilter = securityFilters.getFilters(path).stream()
                .filter(OAuth2LoginAuthenticationFilter.class::isInstance)
                .map(OAuth2LoginAuthenticationFilter.class::cast)
                .findFirst().orElseThrow();
        AuthenticationManager original = (AuthenticationManager) ReflectionTestUtils.getField(
                callbackFilter, "authenticationManager");

        // replace provider token validation while exercising the real browser callback filter and session handoff
        callbackFilter.setAuthenticationManager(authentication -> {
            OAuth2LoginAuthenticationToken request = (OAuth2LoginAuthenticationToken) authentication;
            OidcUser identity = mock(OidcUser.class);
            when(identity.getName()).thenReturn(subject);
            when(identity.getSubject()).thenReturn(subject);
            when(identity.getEmailVerified()).thenReturn(true);
            when(identity.getClaimAsString("hd")).thenReturn("example.test");
            when(identity.getClaimAsString("tid")).thenReturn("11111111-1111-1111-1111-111111111111");
            OAuth2AccessToken accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,
                    "test-provider-token", Instant.now(), Instant.now().plusSeconds(60));
            return new OAuth2LoginAuthenticationToken(request.getClientRegistration(),
                    request.getAuthorizationExchange(), identity, List.of(), accessToken);
        });
        try {
            return mvc.perform(get(path).session(session).param("code", "test-provider-code").param("state", state))
                    .andExpect(status().is3xxRedirection())
                    .andReturn();
        } finally {
            callbackFilter.setAuthenticationManager(original);
        }
    }

    private String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private User activeUser() {
        FinancialInstitution institution = new FinancialInstitution();
        institution.setStatus(FinancialInstitutionStatus.ACTIVE);
        User user = new User();
        user.setId(7L);
        user.setEmail("user@example.test");
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerified(true);
        user.setFinancialInstitution(institution);
        return user;
    }
}
