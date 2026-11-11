package com.example.authservice.documentation;

import com.example.authservice.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import java.util.Map;

// Swagger documentation is separate from controller business logic.
@Tag(
        name = "Email Verification",
        description = "Verify registration emails using six-digit codes, resend codes and change pending email addresses."
)
public interface VerificationCodeApi {

    @Operation(
            summary = "Resume verification for an unverified account after checking its password",
            description = """
                    Resumes email verification for an existing LimitGuard account that has not completed email verification.

                    **Business Rules:**
                    - The user must provide their registered email address and password.
                    - The account must still be awaiting email verification.
                    - The submitted credentials must be valid.
                    - A verification challenge is returned with expiration and resend timing information.
                    - The six-digit verification code is sent by email and is never included in the API response.

                    **Security:**
                    - No JWT Bearer authentication required.
                    - Valid account credentials are required to resume verification.

                    **Responses:**
                    - `200 OK` — Verification challenge returned.
                    - `400 Bad Request` — Invalid request details.

                    **Next Step:**
                    - Enter the six-digit verification code received by email to complete registration.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Verification challenge returned"),
            @ApiResponse(responseCode = "400", description = "Invalid request details")
    })
    ResponseEntity<VerificationCodeResponse> request(LoginRequest request);

    @Operation(
            summary = "Verify a pending registration using its six-digit email code",
            description = """
                    Verifies the email address associated with a pending LimitGuard registration.

                    **Business Rules:**
                    - A valid verification challenge ID is required.
                    - The verification code must contain exactly six digits.
                    - Leading zeros are supported.
                    - Incorrect, expired or unavailable codes are rejected.
                    - Successful verification marks the account's email as verified.

                    **Security:**
                    - No JWT Bearer authentication required.
                    - A valid challenge ID and verification code are required.

                    **Responses:**
                    - `200 OK` — Email verified successfully.
                    - `400 Bad Request` — Incorrect, expired or unavailable verification code.

                    **Next Step:**
                    - The user can sign in using their registered email address and password, provided the account is otherwise eligible.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Email verified successfully"),
            @ApiResponse(responseCode = "400", description = "Incorrect, expired or unavailable code")
    })
    ResponseEntity<Map<String, String>> verify(VerificationCodeRequest request);

    @Operation(
            summary = "Send a new code after the 60-second resend cooldown",
            description = """
                    Resends a six-digit email verification code for an existing LimitGuard registration challenge.

                    **Business Rules:**
                    - A valid verification challenge ID must be provided.
                    - The registration must still be awaiting email verification.
                    - A new code can only be requested after the 60-second resend cooldown.
                    - The new verification code is delivered to the registered email address.
                    - The API response includes challenge and timing information, but never the verification code.

                    **Security:**
                    - No JWT Bearer authentication required.
                    - A valid verification challenge ID is required.
                    - Resend restrictions help prevent repeated code requests.

                    **Responses:**
                    - `200 OK` — Verification code resent successfully.
                    - `400 Bad Request` — Invalid verification request.

                    **Next Step:**
                    - Enter the latest six-digit verification code to complete email verification.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Verification code resent"),
            @ApiResponse(responseCode = "400", description = "Invalid verification request")
    })
    ResponseEntity<VerificationCodeResponse> resend(VerificationResendRequest request);

    @Operation(
            summary = "Change the email on this pending registration and send a new code",
            description = """
                    Changes the email address associated with a pending LimitGuard registration and sends a new verification code.

                    **Business Rules:**
                    - A valid verification challenge ID must be provided.
                    - The account must still be awaiting email verification.
                    - The new email address must have a valid format.
                    - The new email address must satisfy account uniqueness requirements.
                    - A new six-digit verification code is sent to the updated email address.
                    - The API returns challenge and timing information without exposing the verification code.

                    **Security:**
                    - No JWT Bearer authentication required.
                    - A valid verification challenge ID is required.
                    - Email verification must be completed before the new address can be used for login.

                    **Responses:**
                    - `200 OK` — Email updated and verification challenge returned.
                    - `400 Bad Request` — Invalid email or verification request.

                    **Next Step:**
                    - Enter the six-digit code sent to the new email address to complete registration.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Email updated and verification challenge returned"),
            @ApiResponse(responseCode = "400", description = "Invalid email or verification request")
    })
    ResponseEntity<VerificationCodeResponse> changeEmail(VerificationEmailChangeRequest request);
}
