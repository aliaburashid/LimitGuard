
package com.example.limitguard.dto;

import jakarta.validation.constraints.Email;
import lombok.Getter;
import lombok.Setter;
import io.swagger.v3.oas.annotations.media.Schema;

@Getter
@Setter
public class UpdateProfileRequest {

    @Schema(
            description = "New first name of the authenticated user",
            example = "Sara",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED
    )
    private String firstName;

    @Schema(
            description = "New last name of the authenticated user",
            example = "Ahmed",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED
    )
    private String lastName;

    // the user can update their email, but it must be a valid email address
    @Schema(
            description = "New email address of the authenticated user. Must be a valid email address",
            example = "sara.ahmed@example.com",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED
    )
    @Email(message = "Email must be valid")
    private String email;
}
