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

    /**
     * Creates a consistent error response for all handled API exceptions
     * Keeping this logic in one place ensures that error responses
     * contain the same information regardless of the exception type
     */
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

    /**
     * Converts an exception into an HTTP response while preserving
     * the standard LimitGuard error response structure
     */
    private ResponseEntity<ErrorResponse> buildErrorResponse(
            HttpStatus status,
            String message,
            HttpServletRequest request) {

        ErrorResponse errorResponse = createErrorResponse(
                status,
                message,
                request
        );

        return new ResponseEntity<>(errorResponse, status);
    }

    /**
     * Handles duplicate records
     * A conflict occurs when the requested resource already exists
     */
    @ExceptionHandler({
            EmailAlreadyExistsException.class,
            FinancialInstitutionAlreadyExistsException.class,
            CounterpartyAlreadyExistsException.class,
            CreditLimitAlreadyExistsException.class,
            CorporateGroupAlreadyExistsException.class,
            MembershipExistsException.class
    })
    public ResponseEntity<ErrorResponse> handleAlreadyExists(
            RuntimeException exception,
            HttpServletRequest request) {

        return buildErrorResponse(
                HttpStatus.CONFLICT,
                exception.getMessage(),
                request
        );
    }

    /**
     * Handles invalid business operations and request arguments
     * These errors indicate that the request cannot be processed
     * because it violates a validation or business rule
     */
    @ExceptionHandler({
            InvalidTokenException.class,
            IncorrectPasswordException.class,
            IllegalArgumentException.class,
            InvalidCreditLimitReductionException.class,
            InsufficientHeadroomException.class,
            InvalidCreditRequestOperationException.class,
            InvalidCreditRequestStateException.class,
            InvalidCorporateGroupException.class,
            InvalidMembershipException.class
    })
    public ResponseEntity<ErrorResponse> handleBadRequest(
            RuntimeException exception,
            HttpServletRequest request) {

        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                exception.getMessage(),
                request
        );
    }

    /**
     * Handles login failures
     * The same error message is returned for an unknown email
     * and an incorrect password to avoid revealing which
     * email addresses are registered
     */
    @ExceptionHandler({
            BadCredentialsException.class,
            UsernameNotFoundException.class
    })
    public ResponseEntity<ErrorResponse> handleAuthenticationFailure(
            RuntimeException exception,
            HttpServletRequest request) {

        return buildErrorResponse(
                HttpStatus.UNAUTHORIZED,
                "Invalid email or password",
                request
        );
    }

    /**
     * Handles attempts to log in before email verification
     * The account exists, but access is not yet permitted
     */
    @ExceptionHandler(EmailNotVerifiedException.class)
    public ResponseEntity<ErrorResponse> handleEmailNotVerified(
            EmailNotVerifiedException exception,
            HttpServletRequest request) {

        return buildErrorResponse(
                HttpStatus.FORBIDDEN,
                exception.getMessage(),
                request
        );
    }

    /**
     * Handles login attempts from deactivated user accounts
     */
    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ErrorResponse> handleDisabledUser(
            DisabledException exception,
            HttpServletRequest request) {

        return buildErrorResponse(
                HttpStatus.FORBIDDEN,
                "This account has been deactivated",
                request
        );
    }

    /**
     * Handles request body validation failures
     * Field names are included so clients can identify
     * which values need to be corrected
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {

        String validationErrors = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));

        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                validationErrors,
                request
        );
    }

    /**
     * Handles requests for resources that do not exist
     * All resource-not-found exceptions return the same HTTP status
     * while preserving their individual exception messages
     */
    @ExceptionHandler({
            FinancialInstitutionNotFoundException.class,
            UserNotFoundException.class,
            CounterpartyNotFoundException.class,
            CreditLimitNotFoundException.class,
            CreditRequestNotFoundException.class,
            CorporateGroupNotFoundException.class,
            MembershipNotFoundException.class
    })
    public ResponseEntity<ErrorResponse> handleNotFound(
            RuntimeException exception,
            HttpServletRequest request) {

        return buildErrorResponse(
                HttpStatus.NOT_FOUND,
                exception.getMessage(),
                request
        );
    }

    /**
     * Handles file upload failures
     * Internal file-system details are not exposed to API clients
     */
    @ExceptionHandler(IOException.class)
    public ResponseEntity<ErrorResponse> handleIOException(
            IOException exception,
            HttpServletRequest request) {

        return buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Unable to save profile picture",
                request
        );
    }

    /**
     * Handles email delivery failures.
     * A general error message prevents internal mail
     * configuration details from being exposed
     */
    @ExceptionHandler(MailSendException.class)
    public ResponseEntity<ErrorResponse> handleMailSendException(
            MailSendException exception,
            HttpServletRequest request) {

        return buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Unable to send email",
                request
        );
    }
}
