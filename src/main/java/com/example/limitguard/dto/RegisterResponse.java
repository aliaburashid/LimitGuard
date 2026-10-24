
package com.example.limitguard.dto;

import com.example.limitguard.enums.UserRole;
import com.example.limitguard.enums.UserStatus;
import lombok.Getter;
import lombok.Setter;
import io.swagger.v3.oas.annotations.media.Schema;

// The information we send back after a user registers successfully

@Getter
@Setter
public class RegisterResponse {

    // The ID of the newly created user
    @Schema(
            description = "Unique identifier assigned to the newly registered user",
            example = "1",
            accessMode = Schema.AccessMode.READ_ONLY
    )
    private Long id;

    // The user's first name
    @Schema(
            description = "First name of the registered user",
            example = "Sara",
            accessMode = Schema.AccessMode.READ_ONLY
    )
    private String firstName;

    // The user's last name
    @Schema(
            description = "Last name of the registered user",
            example = "Ahmed",
            accessMode = Schema.AccessMode.READ_ONLY
    )
    private String lastName;

    // The user's email address
    @Schema(
            description = "Email address associated with the registered account",
            example = "sara.ahmed@example.com",
            accessMode = Schema.AccessMode.READ_ONLY
    )
    private String email;

    // The role given to the new user
    @Schema(
            description = "Role assigned to the newly registered user",
            accessMode = Schema.AccessMode.READ_ONLY
    )
    private UserRole role;

    // Shows if the user's account is active
    @Schema(
            description = "Current account status of the registered user",
            accessMode = Schema.AccessMode.READ_ONLY
    )
    private UserStatus status;

    // Shows if the user has verified their email
    @Schema(
            description = "Indicates whether the user has verified their email address",
            example = "false",
            accessMode = Schema.AccessMode.READ_ONLY
    )
    private boolean emailVerified;
}
