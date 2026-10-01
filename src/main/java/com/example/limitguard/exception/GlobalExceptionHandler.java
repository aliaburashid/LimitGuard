package com.example.limitguard.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.HashMap;
import java.util.Map;

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
}