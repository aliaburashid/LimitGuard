package com.example.authservice.documentation;

import com.example.authservice.dto.LoginResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;

// Swagger documentation is kept separate from controller business logic.
@Tag(
        name = "Work Account SSO",
        description = "Google Workspace and Microsoft work-account sign-in, provider availability and one-time JWT exchange."
)
public interface SsoApi {

    @Operation(
            summary = "Check which work sign-in providers are configured",
            description = """
                    Returns the availability of Google and Microsoft work-account sign-in providers.

                    **Business Rules:**
                    - Returns a boolean value for each supported provider.
                    - `google` indicates whether Google SSO is configured and enabled.
                    - `microsoft` indicates whether Microsoft SSO is configured and enabled.
                    - Provider credentials and secrets are never exposed.
                    - Provider availability does not guarantee that an individual account is authorized.

                    **Security:**
                    - No JWT Bearer authentication required.
                    - This endpoint is publicly accessible.

                    **Responses:**
                    - `200 OK` — Provider availability returned.

                    **Next Step:**
                    - Start the OAuth2 login flow for an enabled provider.
                    """
    )
    @ApiResponse(
            responseCode = "200",
            description = "Configured provider availability",
            content = @Content(
                    mediaType = "application/json",
                    examples = @ExampleObject(value = """
                            {
                              "google": true,
                              "microsoft": false
                            }
                            """)
            )
    )
    Map<String, Boolean> providers();

    @Operation(
            summary = "Exchange a successful provider login once for a LimitGuard JWT",
            description = """
                    Exchanges a successfully authenticated work-account session for a LimitGuard JWT.

                    **Business Rules:**
                    - Google accounts must have verified email addresses and belong to an approved Workspace domain.
                    - Microsoft accounts must belong to the configured tenant.
                    - The provider identity must be explicitly mapped to an existing LimitGuard user.
                    - The LimitGuard user and financial institution must both be active.
                    - The user's email must be verified.
                    - The SSO session must have completed within the previous 60 seconds.
                    - Each completed SSO session can be exchanged only once.
                    - A successful exchange returns a LimitGuard JWT in the `message` field.
                    - The JSON request body does not contain provider credentials; the existing HTTP session is used.

                    **Security:**
                    - No existing LimitGuard JWT required.
                    - A successfully authenticated Google or Microsoft OIDC session is required.
                    - Expired, missing, consumed or unauthorized sessions are rejected.

                    **Responses:**
                    - `200 OK` — LimitGuard JWT issued.
                    - `401 Unauthorized` — SSO session invalid or account access denied.

                    **Next Step:**
                    - Include the issued JWT in the Authorization header as `Bearer <token>` when accessing protected endpoints.
                    """
    )
    @ApiResponse(
            responseCode = "200",
            description = "LimitGuard JWT issued",
            content = @Content(
                    mediaType = "application/json",
                    examples = @ExampleObject(value = """
                            {
                              "message": "example.jwt.token"
                            }
                            """)
            )
    )
    @ApiResponse(
            responseCode = "401",
            description = "SSO session missing, expired, already consumed or account access denied",
            content = @Content(
                    mediaType = "application/json",
                    examples = @ExampleObject(value = """
                            {
                              "status": 401,
                              "error": "Unauthorized"
                            }
                            """)
            )
    )
    ResponseEntity<LoginResponse> exchange(
            HttpServletRequest request,
            Map<String, Object> body
    );
}
