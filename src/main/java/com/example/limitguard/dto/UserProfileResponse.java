
package com.example.limitguard.dto;

import com.example.limitguard.model.FinancialInstitution;
import com.example.limitguard.enums.UserRole;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import io.swagger.v3.oas.annotations.media.Schema;

@Getter
@Setter
@AllArgsConstructor

// shouldn't return the entire User entity from the profile endpoint
// as the User contains things we don't want to expose such as password
public class UserProfileResponse {

    // basic information about the logged-in user
    @Schema(
            description = "Unique identifier of the user",
            example = "1",
            accessMode = Schema.AccessMode.READ_ONLY
    )
    private Long id;

    @Schema(
            description = "First name of the user",
            example = "Sara",
            accessMode = Schema.AccessMode.READ_ONLY
    )
    private String firstName;

    @Schema(
            description = "Last name of the user",
            example = "Ahmed",
            accessMode = Schema.AccessMode.READ_ONLY
    )
    private String lastName;

    @Schema(
            description = "Registered email address of the user",
            example = "sara.ahmed@example.com",
            accessMode = Schema.AccessMode.READ_ONLY
    )
    private String email;

    // the users role inside LimitGuard
    @Schema(
            description = "Role assigned to the user within LimitGuard",
            example = "ADMIN",
            accessMode = Schema.AccessMode.READ_ONLY
    )
    private UserRole role;

    // The financial institution the user belongs to
    @Schema(
            description = "Financial institution associated with the user",
            accessMode = Schema.AccessMode.READ_ONLY
    )
    private FinancialInstitution financialInstitution;

    @Schema(
            description = "Path or URL of the user's uploaded profile picture",
            example = "/uploads/profiles/profile-1.jpg",
            accessMode = Schema.AccessMode.READ_ONLY
    )
    private String profilePicturePath;
}
