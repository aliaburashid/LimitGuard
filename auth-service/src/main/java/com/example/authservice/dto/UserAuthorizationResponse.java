package com.example.authservice.dto;

import com.example.authservice.enums.UserRole;
import com.example.authservice.enums.UserStatus;
import lombok.*;

// DTO containing the authorization information Credit Service needs
@Getter
@Setter
@NoArgsConstructor // Empty constructor used when converting JSON into this class.
@AllArgsConstructor // Constructor that accepts all four fields.
public class UserAuthorizationResponse {

    // Unique ID of the authenticated user.
    private Long userId;

    // Current role of the user.
    private UserRole role;

    // Current account status.
    private UserStatus status;

    // ID of the user's financial institution.
    private Long financialInstitutionId;
}
