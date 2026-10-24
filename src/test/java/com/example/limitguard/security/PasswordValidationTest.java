package com.example.limitguard.security;

import com.example.limitguard.dto.RegisterRequest;
import com.example.limitguard.dto.ChangePasswordRequest;
import com.example.limitguard.dto.ResetPasswordRequest;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// tests the password validation rules in our DTOs
public class PasswordValidationTest {

    // Validator checks annotations such as @NotBlank, @Size and @Pattern
    private static Validator validator;

    // factory used to create our Validator
    private static ValidatorFactory factory;

    // runs once before all tests
    @BeforeAll
    static void setUp() {
        // create the validator using Jakarta Validation
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    // runs once after all tests
    @AfterAll
    static void tearDown() {
        // release resources used by the validator
        factory.close();
    }

    // tests that weak passwords are rejected during registration
    @Test
    void shouldRejectWeakRegistrationPassword() {

        // create a registration request
        RegisterRequest request = new RegisterRequest();

        // set an invalid password
        request.setPassword("hello123");

        // check the password field against its validation annotations
        boolean passwordInvalid = validator.validateProperty(
                request, "password"
        ).size() > 0;

        // verify that the weak password is rejected
        assertTrue(passwordInvalid);
    }

    // tests that weak passwords are rejected when changing a password
    @Test
    void shouldRejectWeakChangedPassword() {

        // create a change password request
        ChangePasswordRequest request = new ChangePasswordRequest();

        // set an invalid new password
        request.setNewPassword("hello123");

        // check the newPassword field
        boolean passwordInvalid = validator.validateProperty(
                request, "newPassword"
        ).size() > 0;

        // verify that the weak password is rejected
        assertTrue(passwordInvalid);
    }

    // tests that weak passwords are rejected during password reset
    @Test
    void shouldRejectWeakResetPassword() {

        // create a password reset request
        ResetPasswordRequest request = new ResetPasswordRequest();

        // set an invalid new password
        request.setNewPassword("hello123");

        // check the newPassword field
        boolean passwordInvalid = validator.validateProperty(
                request, "newPassword"
        ).size() > 0;

        // verify that the weak password is rejected
        assertTrue(passwordInvalid);
    }

    // tests that a strong password passes registration validation
    @Test
    void shouldAcceptStrongRegistrationPassword() {

        // create a registration request
        RegisterRequest request = new RegisterRequest();

        // set a password that meets all requirements
        request.setPassword("Secure123!");

        // validate the password field
        boolean passwordValid = validator.validateProperty(
                request, "password"
        ).isEmpty();

        // verify that the strong password is accepted
        assertTrue(passwordValid);
    }
}
