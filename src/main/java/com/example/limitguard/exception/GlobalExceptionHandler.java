package com.example.limitguard.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

// Handles exceptions from all controllers in the application
@RestControllerAdvice
public class GlobalExceptionHandler {

    // If an EmailAlreadyExistsException happens, run this method
    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<Map<String, String>> handleEmailAlreadyExists(
            EmailAlreadyExistsException exception) {

        // Create JSON containing the error response
        Map<String, String> errorResponse = new HashMap<>();

        // Add the exception message to the response
        errorResponse.put("message", exception.getMessage());

        // Return the error with status 409 CONFLICT
        return new ResponseEntity<>(errorResponse, HttpStatus.CONFLICT);
    }
}