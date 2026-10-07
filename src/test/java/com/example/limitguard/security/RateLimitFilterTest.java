
package com.example.limitguard.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RateLimitFilterTest {

    private RateLimitFilter rateLimitFilter;
    private FilterChain filterChain;

    // says run this method before every @Test method.
    @BeforeEach
    void setUp() {

        // create a new rate limiter before each test
        // this makes sure every test starts with zero requests
        rateLimitFilter = new RateLimitFilter();

        // mock the next filter in the security chain
        // this allows us to test without starting the server
        filterChain = mock(FilterChain.class);
    }

    // test 1: check that the first five login requests are allowed
    // send five POST requests from the same IP address
    // expected result: all five requests pass through the filter without HTTP 429
    @Test
    void shouldAllowFirstFiveLoginRequests() throws Exception {

        // send five login requests from the same IP address
        for (int i = 0; i < 5; i++) {

            // create a fake POST login request
            MockHttpServletRequest request =
                    new MockHttpServletRequest(
                            "POST", "/api/auth/users/login");

            request.setRemoteAddr("192.168.1.10");

            // create a fake HTTP response
            MockHttpServletResponse response =
                    new MockHttpServletResponse();

            // pass the request through the rate limiter
            rateLimitFilter.doFilterInternal(
                    request, response, filterChain);

            // check that the request was not blocked
            assertNotEquals(429, response.getStatus());
        }

        // check that all five requests reached the next filter
        verify(filterChain, times(5)).doFilter(any(), any());
    }


    // test 2: check that the rate limiter blocks excessive login attempts
    // send six POST requests from the same IP address within one minute
    // expected result: the first five pass and the sixth returns HTTP 429
    // also check that the response contains the TOO_MANY_REQUESTS error
    @Test
    void shouldBlockSixthLoginRequest() throws Exception {

        // send six login requests from the same IP address
        for (int i = 0; i < 6; i++) {

            MockHttpServletRequest request =
                    new MockHttpServletRequest(
                            "POST", "/api/auth/users/login");

            request.setRemoteAddr("192.168.1.20");

            MockHttpServletResponse response =
                    new MockHttpServletResponse();

            // check each request using the rate limiter
            rateLimitFilter.doFilterInternal(
                    request, response, filterChain);

            if (i == 5) {

                // the sixth request should return HTTP 429
                assertEquals(429, response.getStatus());

                // check that the response contains the correct error
                assertTrue(response.getContentAsString()
                        .contains("TOO_MANY_REQUESTS"));
            }
        }

        // only five requests should reach the next filter
        verify(filterChain, times(5)).doFilter(any(), any());
    }


    // test 3: check that rate limiting only applies to selected auth endpoints
    // send ten GET requests to the counterparties endpoint
    // expected result: all ten requests pass through without HTTP 429
    @Test
    void shouldNotLimitOtherEndpoints() throws Exception {

        // send ten requests to an endpoint without rate limiting
        for (int i = 0; i < 10; i++) {

            MockHttpServletRequest request =
                    new MockHttpServletRequest(
                            "GET", "/api/counterparties");

            MockHttpServletResponse response =
                    new MockHttpServletResponse();

            // pass the request through the rate limiter
            rateLimitFilter.doFilterInternal(
                    request, response, filterChain);

            // check that the request is not blocked
            assertNotEquals(429, response.getStatus());
        }

        // check that all ten requests reached the next filter
        verify(filterChain, times(10)).doFilter(any(), any());
    }


    // test 4: check that registration is protected by rate limiting
    // send six registration requests from the same IP address
    // expected result: the first five pass and the sixth returns HTTP 429
    @Test
    void shouldBlockSixthRegistrationRequest() throws Exception {

        for (int i = 0; i < 6; i++) {

            MockHttpServletRequest request =
                    new MockHttpServletRequest(
                            "POST", "/api/auth/users/register");

            request.setRemoteAddr("192.168.1.30");

            MockHttpServletResponse response =
                    new MockHttpServletResponse();

            rateLimitFilter.doFilterInternal(
                    request, response, filterChain);

            if (i == 5) {
                assertEquals(429, response.getStatus());
            }
        }

        // check that only five requests reached the next filter
        verify(filterChain, times(5)).doFilter(any(), any());
    }



    // test 5: check that password recovery is protected by rate limiting
    // send six forgot-password requests from the same IP address
    // expected result: the first five pass and the sixth returns HTTP 429
    @Test
    void shouldBlockSixthPasswordRecoveryRequest() throws Exception {

        for (int i = 0; i < 6; i++) {

            // create a fake password recovery request
            MockHttpServletRequest request =
                    new MockHttpServletRequest(
                            "POST", "/api/auth/users/forgot-password");

            request.setRemoteAddr("192.168.1.40");

            MockHttpServletResponse response =
                    new MockHttpServletResponse();

            // pass the request through the rate limiter
            rateLimitFilter.doFilterInternal(
                    request, response, filterChain);

            if (i == 5) {

                // check that the sixth request is blocked
                assertEquals(429, response.getStatus());

                // check that the correct error message is returned
                assertTrue(response.getContentAsString()
                        .contains("TOO_MANY_REQUESTS"));
            }
        }

        // check that only five requests reached the next filter
        verify(filterChain, times(5)).doFilter(any(), any());
    }


    // test 6: check that login and registration have separate rate limits
    // send five login requests and one registration request from the same IP
    // expected result: registration is still allowed after five login requests
    @Test
    void shouldTrackEndpointsSeparately() throws Exception {

        String clientIp = "192.168.1.50";

        // use all five allowed login requests
        for (int i = 0; i < 5; i++) {

            MockHttpServletRequest loginRequest =
                    new MockHttpServletRequest(
                            "POST", "/api/auth/users/login");

            loginRequest.setRemoteAddr(clientIp);

            MockHttpServletResponse loginResponse =
                    new MockHttpServletResponse();

            rateLimitFilter.doFilterInternal(
                    loginRequest, loginResponse, filterChain);

            assertNotEquals(429, loginResponse.getStatus());
        }

        // create a registration request from the same IP
        MockHttpServletRequest registerRequest =
                new MockHttpServletRequest(
                        "POST", "/api/auth/users/register");

        registerRequest.setRemoteAddr(clientIp);

        MockHttpServletResponse registerResponse =
                new MockHttpServletResponse();

        // check whether registration is allowed
        rateLimitFilter.doFilterInternal(
                registerRequest, registerResponse, filterChain);

        // registration should not be blocked by login attempts
        assertNotEquals(429, registerResponse.getStatus());

        // all six requests should reach the next filter
        verify(filterChain, times(6)).doFilter(any(), any());
    }


    // test 7: check that the request counter resets after one minute
    // send five login requests, then simulate 61 seconds passing
    // expected result: a new login request is allowed after the time window expires
    @Test
    void shouldResetRateLimitAfterOneMinute() throws Exception {

        // create a filter with a clock we can control
        final long[] fakeTime = {1_000_000L};

        rateLimitFilter = new RateLimitFilter() {
            @Override
            protected long getCurrentTime() {
                return fakeTime[0];
            }
        };

        // send five login requests to reach the limit
        for (int i = 0; i < 5; i++) {

            MockHttpServletRequest request =
                    new MockHttpServletRequest(
                            "POST", "/api/auth/users/login");

            request.setRemoteAddr("192.168.1.60");

            MockHttpServletResponse response =
                    new MockHttpServletResponse();

            rateLimitFilter.doFilterInternal(
                    request, response, filterChain);

            assertNotEquals(429, response.getStatus());
        }

        // move the fake clock forward by 61 seconds
        fakeTime[0] += 61_000;

        // send another login request after the time window expires
        MockHttpServletRequest request =
                new MockHttpServletRequest(
                        "POST", "/api/auth/users/login");

        request.setRemoteAddr("192.168.1.60");

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        rateLimitFilter.doFilterInternal(
                request, response, filterChain);

        // the new request should be allowed because the counter reset
        assertNotEquals(429, response.getStatus());

        // all six requests should reach the next filter
        verify(filterChain, times(6)).doFilter(any(), any());
    }



}
