package com.example.creditservice.exception;

import com.example.creditservice.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private ResponseEntity<ErrorResponse> buildErrorResponse(
            HttpStatus status,
            String message,
            HttpServletRequest request) {

        ErrorResponse errorResponse = new ErrorResponse(
                LocalDateTime.now(),
                status.value(),
                status.name(),
                message,
                request.getRequestURI()
        );

        return new ResponseEntity<>(errorResponse, status);
    }

    // Duplicate credit-related records
    @ExceptionHandler({
            CounterpartyAlreadyExistsException.class,
            CreditLimitAlreadyExistsException.class,
            CorporateGroupAlreadyExistsException.class,
            MembershipExistsException.class,
            GroupCreditLimitAlreadyExistsException.class
    })
    public ResponseEntity<ErrorResponse> handleAlreadyExists(
            RuntimeException exception,
            HttpServletRequest request) {

        return buildErrorResponse(
                HttpStatus.CONFLICT, exception.getMessage(), request);
    }

    // Invalid credit operations
    @ExceptionHandler({
            IllegalArgumentException.class,
            InvalidCreditLimitReductionException.class,
            InsufficientHeadroomException.class,
            InvalidCreditRequestOperationException.class,
            InvalidCreditRequestStateException.class,
            InvalidCorporateGroupException.class,
            InvalidMembershipException.class,
            InvalidGroupCreditLimitException.class
    })
    public ResponseEntity<ErrorResponse> handleBadRequest(
            RuntimeException exception,
            HttpServletRequest request) {

        return buildErrorResponse(
                HttpStatus.BAD_REQUEST, exception.getMessage(), request);
    }

    // Request validation errors
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
                HttpStatus.BAD_REQUEST, validationErrors, request);
    }

    // Missing credit-related resources
    @ExceptionHandler({
            CounterpartyNotFoundException.class,
            CreditLimitNotFoundException.class,
            CreditRequestNotFoundException.class,
            CorporateGroupNotFoundException.class,
            MembershipNotFoundException.class,
            GroupCreditLimitNotFoundException.class,
            FinancialProfileNotFoundException.class
    })
    public ResponseEntity<ErrorResponse> handleNotFound(
            RuntimeException exception,
            HttpServletRequest request) {

        return buildErrorResponse(
                HttpStatus.NOT_FOUND, exception.getMessage(), request);
    }

    // ML prediction failures
    @ExceptionHandler(MlPredictionException.class)
    public ResponseEntity<ErrorResponse> handleMlPredictionException(
            MlPredictionException exception,
            HttpServletRequest request) {

        return buildErrorResponse(
                HttpStatus.BAD_GATEWAY,
                "Unable to obtain ML risk prediction",
                request);
    }

    // Parallel risk assessment failures
    @ExceptionHandler(ParallelRiskAssessmentException.class)
    public ResponseEntity<ErrorResponse> handleParallelRiskAssessmentException(
            ParallelRiskAssessmentException exception,
            HttpServletRequest request) {

        return buildErrorResponse(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Unable to complete parallel risk assessment",
                request);
    }
}
