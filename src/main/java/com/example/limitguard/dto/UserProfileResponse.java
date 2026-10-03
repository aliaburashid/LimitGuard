package com.example.limitguard.dto;

import com.example.limitguard.model.FinancialInstitution;
import com.example.limitguard.model.UserRole;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor

// shouldn't return the entire User entity from the profile endpoint
// as the User contains things we don't want to expose such as password
public class UserProfileResponse {

    // basic information about the logged-in user
    private Long id;
    private String firstName;
    private String lastName;
    private String email;

    // the users role inside LimitGuard
    private UserRole role;

    // The financial institution the user belongs to
    private FinancialInstitution financialInstitution;

    private String profilePicturePath;
}
