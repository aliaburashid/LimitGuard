package com.example.limitguard.dto;

import com.example.limitguard.enums.UserRole;
import com.example.limitguard.enums.UserStatus;
import lombok.Getter;
import lombok.Setter;

// The information we send back after a user registers successfully

@Getter
@Setter
public class RegisterResponse {

    // The ID of the newly created user
    private Long id;

    // The user's first name
    private String firstName;

    // The user's last name
    private String lastName;

    // The user's email address
    private String email;

    // The role given to the new user
    private UserRole role;

    // Shows if the user's account is active
    private UserStatus status;

    // Shows if the user has verified their email
    private boolean emailVerified;
}
