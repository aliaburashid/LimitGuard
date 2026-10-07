
package com.example.limitguard.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    // Maximum number of requests allowed within one time window.
    // Example: 5 login attempts within 1 minute.
    private static final int MAX_REQUESTS = 5;

    // Length of the time window.
    // 60,000 milliseconds = 60 seconds = 1 minute.
    private static final long TIME_WINDOW = 60_000;

    // Stores the request counter for each IP address + endpoint.
    //
    // String = unique key, such as "127.0.0.1:/api/auth/users/login"
    // RequestCounter = number of requests and window start time.
    //
    // ConcurrentHashMap supports requests arriving from multiple
    // threads at the same time.
    private final Map<String, RequestCounter> requestCounters =
            new ConcurrentHashMap<>();


    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        // Get the endpoint the client is trying to access.
        // Example: /api/auth/users/login
        String path = request.getRequestURI();

        // Only apply rate limiting to these public POST endpoints.
        // All other requests continue normally.
        boolean protectedByRateLimit =
                request.getMethod().equals("POST") &&
                        (path.equals("/api/auth/users/login") ||
                                path.equals("/api/auth/users/register") ||
                                path.equals("/api/auth/users/forgot-password"));

        if (!protectedByRateLimit) {
            filterChain.doFilter(request, response);
            return;
        }

        // Get the client's IP address.
        // We use the address reported by the servlet container,
        // rather than trusting an IP header supplied by the client.
        String clientIp = request.getRemoteAddr();

        // Combine the IP and endpoint to create a unique key.
        // This gives login, registration and password recovery
        // separate limits for the same IP address.
        String rateLimitKey = clientIp + ":" + path;

        // Get the current time in milliseconds.
        long currentTime = System.currentTimeMillis();

        // Records whether this request should be blocked.
        // We use an array because a lambda cannot reassign
        // an ordinary local boolean variable.
        final boolean[] blocked = {false};

        // Find and update the counter in one atomic operation.
        // compute() makes sure simultaneous requests for the
        // same key cannot update its counter at the same time.
        requestCounters.compute(rateLimitKey, (key, counter) -> {

            // First request: create a counter starting at 1.
            if (counter == null) {
                return new RequestCounter(currentTime);
            }

            // Calculate how much time has passed since
            // this client's current time window started.
            long timePassed = currentTime - counter.windowStart;

            // If one minute has passed, reset the counter.
            if (timePassed >= TIME_WINDOW) {
                counter.count = 1;
                counter.windowStart = currentTime;
                return counter;
            }

            // If 5 requests have already been made,
            // block the next request without increasing the count.
            if (counter.count >= MAX_REQUESTS) {
                blocked[0] = true;
                return counter;
            }

            // Still below the limit: count this request.
            counter.count++;
            return counter;
        });

        // If the request limit has been exceeded,
        // return HTTP 429 and stop the request.
        if (blocked[0]) {

            // Tell the client to wait before trying again.
            // HTTP 429 means Too Many Requests
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");

            // Match LimitGuard's existing structured error format.
            // The endpoint path comes from our selected route.
            response.getWriter().write(
                    "{"
                            + "\"timestamp\":\"" + LocalDateTime.now() + "\","
                            + "\"status\":429,"
                            + "\"error\":\"TOO_MANY_REQUESTS\","
                            + "\"message\":\"Too many requests. Please try again later.\","
                            + "\"path\":\"" + path + "\""
                            + "}"
            );

            // Do not call filterChain.doFilter().
            // This prevents the request from reaching the controller.
            return;
        }

        // The request is allowed, so continue through Spring.
        filterChain.doFilter(request, response);
    }


    // Helper class that remembers request information
    // for one IP address + endpoint.
    private static class RequestCounter {

        // Number of requests made during the current window.
        private int count;

        // Time when the current 1-minute window started.
        private long windowStart;

        public RequestCounter(long currentTime) {

            // The first request counts as request number 1.
            this.count = 1;

            // Save the starting time of the window.
            this.windowStart = currentTime;
        }
    }
}
