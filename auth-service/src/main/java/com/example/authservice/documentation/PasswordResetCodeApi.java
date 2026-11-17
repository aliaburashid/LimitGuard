package com.example.authservice.documentation;

import com.example.authservice.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import java.util.Map;

// Swagger documentation is separate from controller implementation.
@Tag(
        name = "Password Reset",
        description = "Request, resend and verify six-digit email codes before resetting a password."
)
public interface PasswordResetCodeApi {

    @Operation(
            summary = "Email a six-digit password reset code",
            description = """
                    Starts password recovery using the account's email address.

                    **Business Rules:**
                    - A valid email address is required.
                    - A password reset challenge is created for an eligible account.
                    - A six-digit verification code is delivered by email.
                    - The response includes the challenge ID, email, expiration time and resend availability time.
                    - The verification code and its hash are never returned.
                    - Resending is subject to a cooldown.

                    **Security:**
                    - No JWT Bearer authentication required.
                    - Code verification is required before a reset token can be issued.

                    **Responses:**
                    - `200 OK` — Password reset challenge returned.

                    **Next Step:**
                    - Enter the six-digit code received by email.
                    """
    )
    @ApiResponse(responseCode = "200", description = "Password reset challenge response",
            content = @Content(mediaType = "application/json",
                    examples = @ExampleObject(value = """
                            {
                              "challengeId": "123e4567-e89b-12d3-a456-426614174000",
                              "email": "user@example.com",
                              "expiresAt": "2026-11-10T12:10:00Z",
                              "resendAfter": "2026-11-10T12:01:00Z"
                            }
                            """)))
    ResponseEntity<VerificationCodeResponse> requestCode(ForgotPasswordRequest request);

    @Operation(
            summary = "Resend a password reset code after the cooldown",
            description = """
                    Resends a password reset verification code for an existing challenge.

                    **Business Rules:**
                    - A valid challenge ID is required.
                    - The resend cooldown must have elapsed.
                    - A new six-digit code is emailed when resending is permitted.
                    - The response contains updated challenge and timing information.
                    - The verification code is never exposed in the response.

                    **Security:**
                    - No JWT Bearer authentication required.
                    - A valid password reset challenge is required.
                    - Resend restrictions help prevent repeated requests.

                    **Responses:**
                    - `200 OK` — Password reset code resent successfully.

                    **Next Step:**
                    - Enter the latest six-digit code to continue password recovery.
                    """
    )
    @ApiResponse(responseCode = "200", description = "Updated password reset challenge response",
            content = @Content(mediaType = "application/json",
                    examples = @ExampleObject(value = """
                            {
                              "challengeId": "123e4567-e89b-12d3-a456-426614174000",
                              "email": "user@example.com",
                              "expiresAt": "2026-11-10T12:15:00Z",
                              "resendAfter": "2026-11-10T12:06:00Z"
                            }
                            """)))
    ResponseEntity<VerificationCodeResponse> resendCode(VerificationResendRequest request);

    @Operation(
            summary = "Verify the code before opening the password reset form",
            description = """
                    Verifies a password reset code and issues a temporary reset token.

                    **Business Rules:**
                    - A valid challenge ID is required.
                    - The verification code must contain exactly six digits.
                    - Leading zeros are supported.
                    - Incorrect, expired or unavailable codes are rejected.
                    - Successful verification returns a temporary password reset token.
                    - The reset token is used to authorize the subsequent password change.

                    **Security:**
                    - No JWT Bearer authentication required.
                    - A valid challenge ID and verification code are required.
                    - The reset token must be protected like a credential.

                    **Responses:**
                    - `200 OK` — Verification successful; reset token issued.
                    - `400 Bad Request` — Incorrect, expired or unavailable code.

                    **Next Step:**
                    - Submit the temporary reset token and new password to the password reset endpoint.
                    """
    )
    @ApiResponse(responseCode = "200", description = "Code verified; temporary password reset token issued",
            content = @Content(mediaType = "application/json",
                    examples = @ExampleObject(value = """
                            {
                              "token": "example-reset-token"
                            }
                            """)))
    @ApiResponse(responseCode = "400", description = "Incorrect, expired or unavailable verification code",
            content = @Content(mediaType = "application/json",
                    examples = @ExampleObject(value = """
                            {
                              "message": "The code is incorrect, expired or no longer available. Request a new code if needed."
                            }
                            """)))
    ResponseEntity<Map<String, String>> verifyCode(VerificationCodeRequest request);

}
