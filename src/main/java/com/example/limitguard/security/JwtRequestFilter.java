package com.example.limitguard.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

// Checks the JWT token sent with requests

@Component
public class JwtRequestFilter extends OncePerRequestFilter {

    // Used to load the user from the database using their email
    @Autowired
    private MyUserDetailsService myUserDetailsService;

    // Gives us access to the JWT methods
    @Autowired
    private JWTUtils jwtUtils;


    // Gets the JWT token from the Authorization header
    private String parseJwt(HttpServletRequest request) {

        // Get the Authorization header from the request
        String headerAuth = request.getHeader("Authorization");

        // Check that the header exists and starts with Bearer
        if (StringUtils.hasText(headerAuth)
                && headerAuth.startsWith("Bearer ")) {

            // Remove "Bearer " and return only the JWT token
            return headerAuth.substring(7);
        }

        // No JWT was found
        return null;
    }


    // Runs once for every request sent to LimitGuard
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        try {

            // Get the JWT from the Authorization header
            String jwt = parseJwt(request);

            // Check that a JWT exists and that it is valid
            if (jwt != null && jwtUtils.validateJwtToken(jwt)) {

                // Get the users email stored inside the JWT
                String email = jwtUtils.getUserNameFromJwtToken(jwt);

                // Load the user from the database using their email
                UserDetails userDetails = myUserDetailsService.loadUserByUsername(email);

                // Only authenticate the user if their account is still active
                if (userDetails.isEnabled()) {
                    // Create an authentication object for the logged-in user
                    // this also includes the users roles/authorities
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,
                                    userDetails.getAuthorities()
                            );

                    // add information about the current HTTP request
                    authentication.setDetails(
                            new WebAuthenticationDetailsSource()
                                    .buildDetails(request)
                    );

                    // tell Spring Security that this user is authenticated
                    SecurityContextHolder.getContext()
                            .setAuthentication(authentication);
                }
            }

        } catch (Exception exception) {

            // Print the error if JWT authentication fails
            logger.error(
                    "Cannot set user authentication: "
                            + exception.getMessage()
            );
        }

        // Continue the request to the next security filter
        // and eventually to the controller if access is allowed
        filterChain.doFilter(request, response);
    }
}