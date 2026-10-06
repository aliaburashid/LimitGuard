package com.example.limitguard.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.mail.MailSendException;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.io.IOException;

// Handles exceptions from all controllers in the application
@RestControllerAdvice
public class GlobalExceptionHandler {

    // If an EmailAlreadyExistsException happens, run this method
    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<Map<String, String>> handleEmailAlreadyExists(EmailAlreadyExistsException exception) {
        // Create JSON containing the error response
        Map<String, String> errorResponse = new HashMap<>();
        // Add the exception message to the response
        errorResponse.put("message", exception.getMessage());
        // Return the error with status 409 CONFLICT
        return new ResponseEntity<>(errorResponse, HttpStatus.CONFLICT);
    }


    //  If an InvalidTokenException happens, run this method
    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<Map<String, String>> handleInvalidToken(InvalidTokenException exception) {
        // Create the error response
        Map<String, String> errorResponse = new HashMap<>();
        // Add the exception message to the response
        errorResponse.put("message", exception.getMessage());
        // Return the error with status 400 BAD REQUEST
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    // If an EmailNotVerifiedException happens, run this method
    @ExceptionHandler(EmailNotVerifiedException.class)
    public ResponseEntity<Map<String, String>> handleEmailNotVerified(EmailNotVerifiedException exception) {
        // Create the error response that will be returned to Postman
        Map<String, String> errorResponse = new HashMap<>();
        // Add the exception message to the response
        errorResponse.put("message", exception.getMessage());
        // Return 403 Forbidden because the user is not allowed to log in yet
        return new ResponseEntity<>(errorResponse, HttpStatus.FORBIDDEN);
    }

    // If BadCredentialsException happens then run this method
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, String>> handleBadCredentials(BadCredentialsException exception) {
        // Use a general message so we do not reveal which credential was wrong
        Map<String, String> errorResponse = new HashMap<>();
        // Add the exception message to the response
        errorResponse.put("message", "Invalid email or password");
        // Incorrect login credentials should return 401 Unauthorized
        return new ResponseEntity<>(errorResponse, HttpStatus.UNAUTHORIZED);
    }

    // Handles login attempts using an email that does not exist
    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleUserNotFound(UsernameNotFoundException exception) {
        // Use the same message as a wrong password for security
        Map<String, String> errorResponse = new HashMap<>();
        errorResponse.put("message", "Invalid email or password");
        // Incorrect login details should return 401 Unauthorized
        return new ResponseEntity<>(errorResponse, HttpStatus.UNAUTHORIZED);
    }

    // Validation annotations in RegisterRequest, such as @NotBlank,@Email, and @NotNull.
    // When one fails, Spring throws a MethodArgumentNotValidException
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationErrors(MethodArgumentNotValidException exception) {
        // Store each invalid field and its error message
        Map<String, String> errors = new LinkedHashMap<>();
        // Go through all validation errors
        exception.getBindingResult()
                .getFieldErrors()
                .forEach(error ->
                        errors.put(error.getField(), error.getDefaultMessage()));
        // Return the validation errors with 400 Bad Request
        return new ResponseEntity<>(errors, HttpStatus.BAD_REQUEST);
    }


    //  If an FinancialInstitutionNotFoundException happens, run this method
    @ExceptionHandler(FinancialInstitutionNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleFinancialInstitutionNotFound(
            FinancialInstitutionNotFoundException exception) {
        Map<String, String> errorResponse = new HashMap<>();
        // Add the error message to the response
        errorResponse.put("message", exception.getMessage());
        // Return 404 because the financial institution was not found
        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }

    // Handles login attempts from deactivated user accounts
    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<Map<String, String>> handleDisabledUser(DisabledException exception) {
        Map<String, String> errorResponse = new HashMap<>();
        // Explain why the user cannot log in
        errorResponse.put("message", "This account has been deactivated");
        // The account exists but is not allowed to access LimitGuard
        return new ResponseEntity<>(errorResponse, HttpStatus.FORBIDDEN);
    }

    // handles when the current password entered is incorrect
    @ExceptionHandler(IncorrectPasswordException.class)
    public ResponseEntity<Map<String, String>> handleIncorrectPassword(
            IncorrectPasswordException exception) {

        // stores the error message that will be returned to the user
        Map<String, String> error = new HashMap<>();
        error.put("message", exception.getMessage());

        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }


    // handles invalid profile update information
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(
            IllegalArgumentException exception) {

        // stores the error message that will be returned to the user
        Map<String, String> error = new HashMap<>();
        error.put("message", exception.getMessage());

        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    // handles errors that happen while saving an uploaded file
    @ExceptionHandler(IOException.class)
    public ResponseEntity<Map<String, String>> handleIOException(IOException exception) {

        Map<String, String> error = new HashMap<>();
        error.put("message", "Unable to save profile picture");

        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // handles errors that happen while sending an email
    @ExceptionHandler(MailSendException.class)
    public ResponseEntity<Map<String, String>> handleMailSendException(MailSendException exception) {
        Map<String, String> error = new HashMap<>();
        error.put("message", "Unable to send email");
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // handles when a requested user does not exist
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleUserNotFound(UserNotFoundException exception) {

        // stores the error message that will be returned to the user
        Map<String, String> error = new HashMap<>();
        error.put("message", exception.getMessage());

        // returns 404 because the requested user was not found
        return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
    }

    // handles when a financial institution name already exists
    @ExceptionHandler(FinancialInstitutionAlreadyExistsException.class)
    public ResponseEntity<Map<String, String>> handleFinancialInstitutionAlreadyExists(
            FinancialInstitutionAlreadyExistsException exception) {
        // create the error response
        Map<String, String> errorResponse = new HashMap<>();
        // add the error message to the response
        errorResponse.put("message", exception.getMessage());
        // return 409 because the financial institution name already exists
        return new ResponseEntity<>(errorResponse, HttpStatus.CONFLICT);
    }


    // handles when a counterparty name already exists
    @ExceptionHandler(CounterpartyAlreadyExistsException.class)
    public ResponseEntity<Map<String, String>> handleCounterpartyAlreadyExists(
            CounterpartyAlreadyExistsException exception) {
        // create the error response
        Map<String, String> errorResponse = new HashMap<>();
        // add the error message to the response
        errorResponse.put("message", exception.getMessage());
        // return 409 because the counterparty name already exists
        return new ResponseEntity<>(errorResponse, HttpStatus.CONFLICT);
    }

    // handles when a counterparty cannot be found
    @ExceptionHandler(CounterpartyNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleCounterpartyNotFoundException(
            CounterpartyNotFoundException exception) {
        return new ResponseEntity<>(Map.of("message", exception.getMessage()), HttpStatus.NOT_FOUND);
    }

    // handles when a credit limit already exists
    @ExceptionHandler(CreditLimitAlreadyExistsException.class)
    public ResponseEntity<Map<String, String>> handleCreditLimitAlreadyExistsException(
            CreditLimitAlreadyExistsException exception) {
        return new ResponseEntity<>(Map.of("message", exception.getMessage()), HttpStatus.CONFLICT);
    }

    // handles when the credit limit is not found
    @ExceptionHandler(CreditLimitNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleCreditLimitNotFoundException(
            CreditLimitNotFoundException exception) {
        return new ResponseEntity<>(Map.of("message", exception.getMessage()), HttpStatus.NOT_FOUND
        );
    }

    // handles invalid credit limit reduction
    @ExceptionHandler(InvalidCreditLimitReductionException.class)
    public ResponseEntity<Map<String, String>> handleInvalidCreditLimitReductionException(
            InvalidCreditLimitReductionException exception) {

        return new ResponseEntity<>(
                Map.of("message", exception.getMessage()),
                HttpStatus.BAD_REQUEST
        );
    }

    // handles credit requests that exceed the available headroom
    @ExceptionHandler(InsufficientHeadroomException.class)
    public ResponseEntity<Map<String, String>> handleInsufficientHeadroom(
            InsufficientHeadroomException exception) {
        return new ResponseEntity<>(Map.of("message", exception.getMessage()), HttpStatus.BAD_REQUEST);
    }

}