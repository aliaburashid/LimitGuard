package com.example.limitguard.documentation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

public interface CreditRequestEventApi {

    @Operation(
            summary = "Subscribe to real-time credit request updates",
            description = "Opens a Server-Sent Events (SSE) connection to receive "
                    + "real-time credit request updates for the authenticated user's "
                    + "financial institution. The connection remains open to deliver "
                    + "events as credit requests change. Requires JWT authentication."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "SSE connection established successfully",
                    content = @Content(
                            mediaType = "text/event-stream",
                            schema = @Schema(type = "string")
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Authentication is required or the JWT is invalid"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Access denied"
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Unable to establish the SSE connection"
            )
    })
    SseEmitter subscribeToCreditRequestUpdates() throws IOException;
}
