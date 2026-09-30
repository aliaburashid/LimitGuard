package com.example.limitguard.controller;

import com.example.limitguard.dto.RegisterRequest;
import com.example.limitguard.dto.RegisterResponse;
import com.example.limitguard.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


// tells Spring that this class contains API endpoints
@RestController
@RequestMapping("/api/auth/users")
public class UserController {

    // Gives the controller access to the user service
    @Autowired
    private UserService userService;

    // Registers a new user
    @PostMapping("/register")
    // takes the JSON request and checks the validation rules in RegisterRequest
    // ResponseEntity lets us return both the data AND an HTTP status code.
    public ResponseEntity<RegisterResponse> registerUser(@Valid @RequestBody RegisterRequest registrationDetails) {

        // sends the registration details to the service to create the new user
        RegisterResponse newUser = userService.registerUser(registrationDetails);

        // Returns the created user with HTTP status 201 CREATED
        return new ResponseEntity<>(newUser, HttpStatus.CREATED);
    }
}
