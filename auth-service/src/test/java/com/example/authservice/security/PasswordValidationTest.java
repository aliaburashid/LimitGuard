package com.example.authservice.security;

import com.example.authservice.dto.RegisterRequest;
import com.example.authservice.dto.ChangePasswordRequest;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class PasswordValidationTest {

    private static Validator validator;

    private static ValidatorFactory factory;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    void shouldRejectWeakRegistrationPassword() {

        RegisterRequest request = new RegisterRequest();

        request.setPassword("hello123");

        boolean passwordInvalid = validator.validateProperty(
                request, "password"
        ).size() > 0;

        assertTrue(passwordInvalid);
    }

    @Test
    void shouldRejectWeakChangedPassword() {

        ChangePasswordRequest request = new ChangePasswordRequest();

        request.setNewPassword("hello123");

        boolean passwordInvalid = validator.validateProperty(
                request, "newPassword"
        ).size() > 0;

        assertTrue(passwordInvalid);
    }

    @Test
    void shouldAcceptStrongRegistrationPassword() {

        RegisterRequest request = new RegisterRequest();

        request.setPassword("Secure123!");

        boolean passwordValid = validator.validateProperty(
                request, "password"
        ).isEmpty();

        assertTrue(passwordValid);
    }
}
