
package com.example.limitguard.dto;

// The information needed when a new user registers
// @NotBlank: is good for Strings because it rejects null, "", and "   "
// @NotNull: works well for financialInstitutionId because it's a Long, not a String
// @Email: checks that the email has a valid email format

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import io.swagger.v3.oas.annotations.media.Schema;

@Getter
@Setter
public class RegisterRequest {

    // The user's first name is required
    @Schema(
            description = "First name of the user",
            example = "Sara",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank(message = "First name is required")
    private String firstName;

    // The user's last name is required
    @Schema(
            description = "Last name of the user",
            example = "Ahmed",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank(message = "Last name is required")
    private String lastName;

    // The email is required and must be a valid email address
    @Schema(
            description = "Unique email address used for registration and login",
            example = "sara.ahmed@example.com",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank(message = "Email is required")
    @Email(message = "Please enter a valid email address")
    private String email;

    // The user's password before it is encrypted
    // The password is required
    @Schema(
            description = "Password containing at least 8 characters, including "
                    + "uppercase and lowercase letters, a number, and a special character",
            example = "SecurePass123!",
            format = "password",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank(message = "Password is required")
    // password must contain at least 8 characters
    @Size(min = 8, message = "Password must be at least 8 characters")
    // password must contain uppercase, lowercase, number and special character
    @Pattern(
            // regex defines the rules that the password must follow
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^a-zA-Z0-9]).+$",
            message = "Password must contain an uppercase letter, lowercase letter, number and special character"
    )
    private String password;

    // The user must belong to a financial institution
    @Schema(
            description = "ID of the financial institution the user belongs to",
            example = "1",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotNull(message = "Financial institution is required")
    private Long financialInstitutionId;
}
