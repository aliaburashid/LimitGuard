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
            description = """
                    Opens a Server-Sent Events (SSE) connection to receive
                    real-time credit request updates.

                    **Features:**
                    - Establishes a persistent HTTP connection for server-to-client notifications.
                    - Delivers updates as credit requests change.
                    - Allows clients to receive updates without repeatedly polling the API.
                    - Uses the `text/event-stream` response format.

                    **Business Rules:**
                    - Events are scoped to the authenticated user's financial institution.
                    - Users receive updates associated with their own institution.
                    - The institution is determined from the authenticated user's identity.

                    **Security:**
                    - Requires JWT Bearer authentication.
                    - Unauthenticated requests return `401 Unauthorized`.
                    - Requests that fail authorization return `403 Forbidden`.

                    **Connection Behaviour:**
                    - The connection remains open to deliver events.
                    - Clients should handle connection interruptions and reconnect when necessary.
                    - The endpoint returns an SSE stream rather than a conventional JSON response.
                    """
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
