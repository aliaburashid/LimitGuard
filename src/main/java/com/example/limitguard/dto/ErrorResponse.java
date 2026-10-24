package com.example.limitguard.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor

// gives us one standard structure
public class ErrorResponse {

    @Schema(
            description = "Date and time when the API error occurred",
            example = "2026-10-24T10:30:00",
            accessMode = Schema.AccessMode.READ_ONLY
    )
    private LocalDateTime timestamp;

    @Schema(
            description = "HTTP status code returned by the API",
            example = "400",
            accessMode = Schema.AccessMode.READ_ONLY
    )
    private int status;

    @Schema(
            description = "Short description of the HTTP error",
            example = "Bad Request",
            accessMode = Schema.AccessMode.READ_ONLY
    )
    private String error;

    @Schema(
            description = "Detailed explanation of why the request failed",
            example = "Credit limit amount must be greater than zero",
            accessMode = Schema.AccessMode.READ_ONLY
    )
    private String message;

    @Schema(
            description = "API endpoint path where the error occurred",
            example = "/api/credit-limits",
            accessMode = Schema.AccessMode.READ_ONLY
    )
    private String path;
}
