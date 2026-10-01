package com.example.limitguard.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

// @Bean tells Spring to create and manage this object

// Tells Spring that this class contains security configuration settings
@Configuration

// Enables method-level security
// Allows us to use annotations such as @PreAuthorize later
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfiguration {

    // Gives Spring Security access to our JWT filter
    @Autowired
    private JwtRequestFilter jwtRequestFilter;

    // Creates the password encoder that will hash users passwords
    @Bean
    public PasswordEncoder passwordEncoder() {
        //BCrypt securely hashes passwords before storing them
        return new BCryptPasswordEncoder();
    }

    // Controls which API endpoints are public and which require login
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                // Disables CSRF protection, Common for stateless REST APIs
                .csrf(csrf -> csrf.disable())

                // Makes the application stateless
                // Spring Security will not store user login sessions
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // Defines which endpoints are public and which require authentication
                .authorizeHttpRequests(auth -> auth

                        // These endpoints can be accessed without logging in
                        .requestMatchers(
                                "/api/auth/users/register",
                                "/api/auth/users/verify-email",
                                "/api/auth/users/login"
                        ).permitAll()

                        // Every other endpoint requires the user to be authenticated
                        .anyRequest().authenticated()
                )

                // check for a JWT before Springs username/password filter
                .addFilterBefore(
                        jwtRequestFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        // Builds and returns the security configuration
        return http.build();
    }

    // Creates the AuthenticationManager that handles user authentication
    // when a user tries to log in
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        // Gets Spring Security's configured AuthenticationManager
        return authConfig.getAuthenticationManager();
    }
}
