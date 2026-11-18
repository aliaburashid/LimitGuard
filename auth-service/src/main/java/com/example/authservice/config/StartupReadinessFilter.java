package com.example.authservice.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Keeps HTTP traffic out until all startup runners (bootstrap or demo seeding) have completed
 * successfully. Docker Compose health checks rely on this to know when Auth data is ready.
 */
@Component
@ConditionalOnExpression("${limitguard.bootstrap.enabled:false} or ${limitguard.seed.demo.enabled:false}")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class StartupReadinessFilter extends OncePerRequestFilter {
    private volatile boolean ready;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        ready = true;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        if (!ready) {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Service Unavailable\",\"message\":\"Authentication service is starting\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
