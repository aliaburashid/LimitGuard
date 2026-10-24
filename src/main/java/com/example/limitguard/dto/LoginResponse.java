
package com.example.limitguard.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import io.swagger.v3.oas.annotations.media.Schema;

// The response returned after a successful login
@Getter
@AllArgsConstructor
public class LoginResponse {

    // Contains the JWT token used for authenticated requests
    @Schema(
            description = "JWT access token returned after successful login. "
                    + "Include this token in the Authorization header "
                    + "using the Bearer authentication scheme.",
            example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJleGFtcGxlIn0.signature",
            accessMode = Schema.AccessMode.READ_ONLY
    )
    private String message;
}
