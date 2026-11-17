package com.example.creditservice.security;

import com.example.creditservice.client.AuthServiceClient;
import com.example.creditservice.dto.AuthContextResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

// Checks the JWT and authenticates users accessing Credit Service
@Component
@RequiredArgsConstructor
public class JwtRequestFilter extends OncePerRequestFilter {

    static final String AUTH_UNAVAILABLE = JwtRequestFilter.class.getName() + ".authUnavailable";

    // Calls Auth Service to retrieve the user's current information
    private final AuthServiceClient authServiceClient;

    // Validates JWT tokens and reads their claims
    private final JWTUtils jwtUtils;

    // Extracts the JWT from the Authorization header
    private String extractJwtToken(HttpServletRequest request) {

        String authorizationHeader = request.getHeader("Authorization");

        // Check whether the request contains a Bearer token
        if (StringUtils.hasText(authorizationHeader)
                && authorizationHeader.startsWith("Bearer ")) {

            // Remove "Bearer " and return the token
            return authorizationHeader.substring(7);
        }

        return null;
    }

    // Runs whenever a request reaches Credit Service
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        try {

            // Extract the JWT from the incoming request
            String jwtToken = extractJwtToken(request);

            // Continue only if the JWT exists and is valid
            if (jwtToken != null && jwtUtils.validateJwtToken(jwtToken)) {

                // Read the user's email from the JWT
                String emailAddress =
                        jwtUtils.getUserNameFromJwtToken(jwtToken);

                // Ask Auth Service for the user's current permissions
                AuthContextResponse userAuthorization =
                        authServiceClient.getAuthContext(jwtToken);

                // Authenticate only active users with complete information
                if (userAuthorization != null
                        && "ACTIVE".equals(userAuthorization.getStatus())
                        && userAuthorization.getUserId() != null
                        && userAuthorization.getRole() != null
                        && !userAuthorization.getRole().isBlank()
                        && userAuthorization.getFinancialInstitutionId() != null) {

                    // Store the authenticated user's information
                    AuthenticatedCreditUser authenticatedCreditUser =
                            new AuthenticatedCreditUser(
                                    userAuthorization.getUserId(),
                                    emailAddress,
                                    userAuthorization.getRole(),
                                    userAuthorization.getFinancialInstitutionId()
                            );

                    // Create Spring Security authentication with the current role
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    authenticatedCreditUser,
                                    jwtToken,
                                    List.of(
                                            new SimpleGrantedAuthority(
                                                    "ROLE_" + userAuthorization.getRole()
                                            )
                                    )
                            );

                    // Add information about the HTTP request
                    authentication.setDetails(
                            new WebAuthenticationDetailsSource()
                                    .buildDetails(request)
                    );

                    // Tell Spring Security that the user is authenticated
                    SecurityContextHolder.getContext()
                            .setAuthentication(authentication);
                }
            }

        } catch (ResourceAccessException | HttpServerErrorException exception) {
            SecurityContextHolder.clearContext();
            // Let the security entry point distinguish an outage on protected routes.
            request.setAttribute(AUTH_UNAVAILABLE, Boolean.TRUE);
            logger.warn("Auth Service unavailable while authenticating Credit Service request");
        } catch (Exception exception) {

            // Ensure authentication fails if JWT validation or Auth Service fails.
            SecurityContextHolder.clearContext();

            logger.warn("Unable to authenticate Credit Service request");
        }

        // Continue through the security filter chain.
        // Protected endpoints must still require authentication.
        filterChain.doFilter(request, response);
    }
}