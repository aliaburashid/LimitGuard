package com.example.creditservice.security;

import jakarta.servlet.DispatcherType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfiguration {

    // Checks JWTs and retrieves current user permissions from Auth Service
    private final JwtRequestFilter jwtRequestFilter;

    // Defines which Credit Service endpoints require authentication
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http)
            throws Exception {

        http
                // Credit Service uses JWTs instead of browser sessions.
                .csrf(csrf -> csrf.disable())

                // Do not store login sessions on the server.
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // Distinguish unavailable Auth from missing or invalid credentials.
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setContentType("application/json");
                            if (Boolean.TRUE.equals(request.getAttribute(JwtRequestFilter.AUTH_UNAVAILABLE))) {
                                response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
                                response.getWriter().write(
                                        "{\"error\":\"Service Unavailable\",\"message\":\"Authentication service is temporarily unavailable\"}"
                                );
                                return;
                            }
                            response.setStatus(HttpStatus.UNAUTHORIZED.value());
                            response.getWriter().write(
                                    "{\"error\":\"Unauthorized\",\"message\":\"Authentication is required\"}"
                            );
                        })
                )

                // Protect Credit Service endpoints
                .authorizeHttpRequests(authorization -> authorization
                        .dispatcherTypeMatchers(
                                DispatcherType.ASYNC,
                                DispatcherType.ERROR
                        ).permitAll()

                        // Swagger needs to load before a user can enter a JWT.
                        .requestMatchers(
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs",
                                "/v3/api-docs/**"
                        ).permitAll()

                        // Every API endpoint requires authentication
                        .anyRequest().authenticated()
                )

                // Validate the JWT before processing protected requests.
                .addFilterBefore(
                        jwtRequestFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }
}