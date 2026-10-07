package com.example.limitguard.exception;

import com.example.limitguard.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.mail.MailSendException;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

// Handles exceptions from all controllers in the application
@RestControllerAdvice
public class GlobalExceptionHandler {

    // Creates the same error response structure for all API errors
    private ErrorResponse createErrorResponse(
            HttpStatus status,
            String message,
            HttpServletRequest request) {

        return new ErrorResponse(
                LocalDateTime.now(),
                status.value(),
                status.name(),
                message,
                request.getRequestURI()
        );
    }


    // If an EmailAlreadyExistsException happens, run this method
    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleEmailAlreadyExists(
            EmailAlreadyExistsException exception,
            HttpServletRequest request) {

        ErrorResponse errorResponse = createErrorResponse(
                HttpStatus.CONFLICT,
                exception.getMessage(),
                request
        );

        // Return the error with status 409 CONFLICT
        return new ResponseEntity<>(errorResponse, HttpStatus.CONFLICT);
    }


    // If an InvalidTokenException happens, run this method
    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidToken(
            InvalidTokenException exception,
            HttpServletRequest request) {

        // Create the error response
        ErrorResponse errorResponse = createErrorResponse(
                HttpStatus.BAD_REQUEST,
                exception.getMessage(),
                request
        );

        // Return the error with status 400 BAD REQUEST
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }


    // If an EmailNotVerifiedException happens, run this method
    @ExceptionHandler(EmailNotVerifiedException.class)
    public ResponseEntity<ErrorResponse> handleEmailNotVerified(
            EmailNotVerifiedException exception,
            HttpServletRequest request) {

        // Create the error response that will be returned to Postman
        ErrorResponse errorResponse = createErrorResponse(
                HttpStatus.FORBIDDEN,
                exception.getMessage(),
                request
        );

        // Return 403 Forbidden because the user is not allowed to log in yet
        return new ResponseEntity<>(errorResponse, HttpStatus.FORBIDDEN);
    }


    // If BadCredentialsException happens then run this method
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(
            BadCredentialsException exception,
            HttpServletRequest request) {

        // Use a general message so we do not reveal which credential was wrong
        ErrorResponse errorResponse = createErrorResponse(
                HttpStatus.UNAUTHORIZED,
                "Invalid email or password",
                request
        );

        // Incorrect login credentials should return 401 Unauthorized
        return new ResponseEntity<>(errorResponse, HttpStatus.UNAUTHORIZED);
    }


    // Handles login attempts using an email that does not exist
    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(
            UsernameNotFoundException exception,
            HttpServletRequest request) {

        // Use the same message as a wrong password for security
        ErrorResponse errorResponse = createErrorResponse(
                HttpStatus.UNAUTHORIZED,
                "Invalid email or password",
                request
        );

        // Incorrect login details should return 401 Unauthorized
        return new ResponseEntity<>(errorResponse, HttpStatus.UNAUTHORIZED);
    }


    // Validation annotations in RegisterRequest, such as @NotBlank,@Email, and @NotNull.
    // When one fails, Spring throws a MethodArgumentNotValidException
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {

        // Store each invalid field and its error message
        String validationErrors = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                // Go through all validation errors
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));

        ErrorResponse errorResponse = createErrorResponse(
                HttpStatus.BAD_REQUEST,
                validationErrors,
                request
        );

        // Return the validation errors with 400 Bad Request
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }


    // If an FinancialInstitutionNotFoundException happens, run this method
    @ExceptionHandler(FinancialInstitutionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleFinancialInstitutionNotFound(
            FinancialInstitutionNotFoundException exception,
            HttpServletRequest request) {

        // Add the error message to the response
        ErrorResponse errorResponse = createErrorResponse(
                HttpStatus.NOT_FOUND,
                exception.getMessage(),
                request
        );

        // Return 404 because the financial institution was not found
        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }


    // Handles login attempts from deactivated user accounts
    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ErrorResponse> handleDisabledUser(
            DisabledException exception,
            HttpServletRequest request) {

        // Explain why the user cannot log in
        ErrorResponse errorResponse = createErrorResponse(
                HttpStatus.FORBIDDEN,
                "This account has been deactivated",
                request
        );

        // The account exists but is not allowed to access LimitGuard
        return new ResponseEntity<>(errorResponse, HttpStatus.FORBIDDEN);
    }


    // handles when the current password entered is incorrect
    @ExceptionHandler(IncorrectPasswordException.class)
    public ResponseEntity<ErrorResponse> handleIncorrectPassword(
            IncorrectPasswordException exception,
            HttpServletRequest request) {

        // stores the error message that will be returned to the user
        ErrorResponse errorResponse = createErrorResponse(
                HttpStatus.BAD_REQUEST,
                exception.getMessage(),
                request
        );

        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }


    // handles invalid profile update information
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException exception,
            HttpServletRequest request) {

        // stores the error message that will be returned to the user
        ErrorResponse errorResponse = createErrorResponse(
                HttpStatus.BAD_REQUEST,
                exception.getMessage(),
                request
        );

        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }


    // handles errors that happen while saving an uploaded file
    @ExceptionHandler(IOException.class)
    public ResponseEntity<ErrorResponse> handleIOException(
            IOException exception,
            HttpServletRequest request) {

        ErrorResponse errorResponse = createErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Unable to save profile picture",
                request
        );

        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }


    // handles errors that happen while sending an email
    @ExceptionHandler(MailSendException.class)
    public ResponseEntity<ErrorResponse> handleMailSendException(
            MailSendException exception,
            HttpServletRequest request) {

        ErrorResponse errorResponse = createErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Unable to send email",
                request
        );

        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }


    // handles when a requested user does not exist
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(
            UserNotFoundException exception,
            HttpServletRequest request) {

        // stores the error message that will be returned to the user
        ErrorResponse errorResponse = createErrorResponse(
                HttpStatus.NOT_FOUND,
                exception.getMessage(),
                request
        );

        // returns 404 because the requested user was not found
        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }


    // handles when a financial institution name already exists
    @ExceptionHandler(FinancialInstitutionAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleFinancialInstitutionAlreadyExists(
            FinancialInstitutionAlreadyExistsException exception,
            HttpServletRequest request) {

        // create the error response
        ErrorResponse errorResponse = createErrorResponse(
                HttpStatus.CONFLICT,
                exception.getMessage(),
                request
        );

        // return 409 because the financial institution name already exists
        return new ResponseEntity<>(errorResponse, HttpStatus.CONFLICT);
    }


    // handles when a counterparty name already exists
    @ExceptionHandler(CounterpartyAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleCounterpartyAlreadyExists(
            CounterpartyAlreadyExistsException exception,
            HttpServletRequest request) {

        // create the error response
        ErrorResponse errorResponse = createErrorResponse(
                HttpStatus.CONFLICT,
                exception.getMessage(),
                request
        );

        // return 409 because the counterparty name already exists
        return new ResponseEntity<>(errorResponse, HttpStatus.CONFLICT);
    }


    // handles when a counterparty cannot be found
    @ExceptionHandler(CounterpartyNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleCounterpartyNotFoundException(
            CounterpartyNotFoundException exception,
            HttpServletRequest request) {

        ErrorResponse errorResponse = createErrorResponse(
                HttpStatus.NOT_FOUND,
                exception.getMessage(),
                request
        );

        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }


    // handles when a credit limit already exists
    @ExceptionHandler(CreditLimitAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleCreditLimitAlreadyExistsException(
            CreditLimitAlreadyExistsException exception,
            HttpServletRequest request) {

        ErrorResponse errorResponse = createErrorResponse(
                HttpStatus.CONFLICT,
                exception.getMessage(),
                request
        );

        return new ResponseEntity<>(errorResponse, HttpStatus.CONFLICT);
    }


    // handles when the credit limit is not found
    @ExceptionHandler(CreditLimitNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleCreditLimitNotFoundException(
            CreditLimitNotFoundException exception,
            HttpServletRequest request) {

        ErrorResponse errorResponse = createErrorResponse(
                HttpStatus.NOT_FOUND,
                exception.getMessage(),
                request
        );

        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }


    // handles invalid credit limit reduction
    @ExceptionHandler(InvalidCreditLimitReductionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCreditLimitReductionException(
            InvalidCreditLimitReductionException exception,
            HttpServletRequest request) {

        ErrorResponse errorResponse = createErrorResponse(
                HttpStatus.BAD_REQUEST,
                exception.getMessage(),
                request
        );

        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }


    // handles credit requests that exceed the available headroom
    @ExceptionHandler(InsufficientHeadroomException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientHeadroom(
            InsufficientHeadroomException exception,
            HttpServletRequest request) {

        ErrorResponse errorResponse = createErrorResponse(
                HttpStatus.BAD_REQUEST,
                exception.getMessage(),
                request
        );

        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }


    // handles credit requests that cannot be found
    @ExceptionHandler(CreditRequestNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleCreditRequestNotFound(
            CreditRequestNotFoundException exception,
            HttpServletRequest request) {

        ErrorResponse errorResponse = createErrorResponse(
                HttpStatus.NOT_FOUND,
                exception.getMessage(),
                request
        );

        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }
}