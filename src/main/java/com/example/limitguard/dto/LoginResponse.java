
package com.example.limitguard.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import io.swagger.v3.oas.annotations.media.Schema;

// The response returned after a successful login
@Getter
@AllArgsConstructor
public class LoginResponse {

    // Contains the JWT token used for authenticated requests
    private String message;
}
