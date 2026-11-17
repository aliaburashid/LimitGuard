package com.example.authservice.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RateLimitFilterTest {

    private static final String USERS = "/api/auth/users";
    private static final String LOGIN = USERS + "/login";

    private long now = 1_000_000L;
    private final RateLimitFilter filter = new RateLimitFilter() {
        @Override
        protected long getCurrentTime() {
            return now;
        }
    };
    private final FilterChain chain = mock(FilterChain.class);

    @ParameterizedTest
    @ValueSource(strings = {"/login", "/register", "/forgot-password"})
    void allowsFiveRequestsAndBlocksTheSixth(String route) throws Exception {
        sendFiveRequests(USERS + route);
        var blocked = send("POST", USERS + route);
        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getContentAsString()).contains("TOO_MANY_REQUESTS");
        verify(chain, times(5)).doFilter(any(), any());
    }

    @Test
    void leavesUnprotectedRequestsUnlimited() throws Exception {
        for (int attempt = 0; attempt < 6; attempt++) {
            assertThat(send("GET", "/api/financial-institutions/directory").getStatus()).isEqualTo(200);
        }
        verify(chain, times(6)).doFilter(any(), any());
    }

    @Test
    void tracksEndpointsSeparatelyForTheSameClient() throws Exception {
        sendFiveRequests(LOGIN);
        assertThat(send("POST", LOGIN).getStatus()).isEqualTo(429);
        assertThat(send("POST", USERS + "/register").getStatus()).isEqualTo(200);
    }

    @Test
    void resetsTheBudgetAfterOneMinute() throws Exception {
        sendFiveRequests(LOGIN);
        assertThat(send("POST", LOGIN).getStatus()).isEqualTo(429);
        now += 60_000;

        assertThat(send("POST", LOGIN).getStatus()).isEqualTo(200);
    }

    private void sendFiveRequests(String path) throws Exception {
        for (int attempt = 0; attempt < 5; attempt++) {
            assertThat(send("POST", path).getStatus()).isEqualTo(200);
        }
    }

    private MockHttpServletResponse send(String method, String path) throws Exception {
        var request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr("192.0.2.1");
        var response = new MockHttpServletResponse();
        filter.doFilterInternal(request, response, chain);
        return response;
    }
}
