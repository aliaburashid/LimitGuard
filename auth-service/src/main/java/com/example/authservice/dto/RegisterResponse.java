
package com.example.authservice.dto;

import com.example.authservice.enums.UserRole;
import com.example.authservice.enums.UserStatus;
import lombok.Getter;
import lombok.Setter;
import io.swagger.v3.oas.annotations.media.Schema;

// The information we send back after a user registers successfully

@Getter
@Setter
public class RegisterResponse {

    // identifies the pending code screen without exposing the code itself
    private VerificationCodeResponse verification;

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
