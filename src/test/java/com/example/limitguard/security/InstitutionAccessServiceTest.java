
package com.example.limitguard.security;

import com.example.limitguard.enums.UserRole;
import com.example.limitguard.model.FinancialInstitution;
import com.example.limitguard.model.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.*;

//  Whether the security helper correctly identifies the logged-in user's
// institution and rejects access to another institution.
class InstitutionAccessServiceTest {

    // creates the real institution access service to test
    private final InstitutionAccessService access = new InstitutionAccessService();

    // clears the logged-in user after each test
    // prevents one test from affecting another test
    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // test that a user must be logged in and belong to a valid institution
    // expect access to be denied if authentication or institution is missing
    @Test
    void requiresAnAuthenticatedAccountWithAnInstitution() {

        // arrange: no user is logged in
        // act and assert: access should be denied
        assertThrows(
                AccessDeniedException.class,
                access::getCurrentInstitutionId
        );

        // arrange: create a user without a financial institution
        User user = new User();
        user.setRole(UserRole.RISK_OFFICER);

        MyUserDetails details = new MyUserDetails(user);

        // simulate a logged-in user
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        details, null, details.getAuthorities()
                )
        );

        // act and assert: access should be denied without an institution
        assertThrows(
                AccessDeniedException.class,
                access::getCurrentInstitutionId
        );

        // arrange: give the user an institution without an ID
        FinancialInstitution institution = new FinancialInstitution();
        user.setFinancialInstitution(institution);

        // act and assert: institution must have a valid ID
        assertThrows(
                AccessDeniedException.class,
                access::getCurrentInstitutionId
        );

        // arrange: assign an institution ID
        institution.setId(1L);

        // simulate authentication without granted authorities
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, null)
        );

        // act and assert: unauthenticated access should still be denied
        assertThrows(
                AccessDeniedException.class,
                access::getCurrentInstitutionId
        );
    }

    // test that an Admin can only access their own financial institution
    // expect access to other institutions to be denied
    @Test
    void adminHasTheSameInstitutionBoundary() {

        // arrange: create financial institution with ID 1
        FinancialInstitution institution = new FinancialInstitution();
        institution.setId(1L);

        // create an Admin belonging to institution 1
        User admin = new User();
        admin.setRole(UserRole.ADMIN);
        admin.setFinancialInstitution(institution);

        MyUserDetails details = new MyUserDetails(admin);

        // simulate the Admin logging in
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        details, null, details.getAuthorities()
                )
        );

        // act and assert: check that the Admin belongs to institution 1
        assertEquals(1L, access.getCurrentInstitutionId());

        // act and assert: Admin can access their own institution
        assertDoesNotThrow(
                () -> access.requireInstitution(1L)
        );

        // act and assert: Admin cannot access another institution
        assertThrows(
                AccessDeniedException.class,
                () -> access.requireInstitution(2L)
        );

        // act and assert: Admin cannot access a missing institution
        assertThrows(
                AccessDeniedException.class,
                () -> access.requireInstitution(null)
        );
    }


    // test that a Risk Officer cannot access another financial institution
    // expect AccessDeniedException when the institution IDs are different
    @Test
    void shouldRejectAccessToAnotherFinancialInstitution() {

        // arrange: create institution 1
        FinancialInstitution institution = new FinancialInstitution();
        institution.setId(1L);

        // create a Risk Officer belonging to institution 1
        User user = new User();
        user.setRole(UserRole.RISK_OFFICER);
        user.setFinancialInstitution(institution);

        // simulate the user being logged in
        MyUserDetails details = new MyUserDetails(user);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        details, null, details.getAuthorities()
                )
        );

        // act and assert: user can access their own institution
        assertDoesNotThrow(
                () -> access.requireInstitution(1L)
        );

        // act and assert: user cannot access institution 2
        assertThrows(
                AccessDeniedException.class,
                () -> access.requireInstitution(2L)
        );
    }

}
