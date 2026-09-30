package com.example.limitguard.service;

import com.example.limitguard.dto.RegisterRequest;
import com.example.limitguard.model.FinancialInstitution;
import com.example.limitguard.model.User;
import com.example.limitguard.repository.FinancialInstitutionRepository;
import com.example.limitguard.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.example.limitguard.dto.RegisterResponse;
import com.example.limitguard.exception.EmailAlreadyExistsException;

// Class that contains business logic
// Create and manage an object of this class for me
@Service
public class UserService {

    // Gives this service access to users in the database
    @Autowired
    private UserRepository userRepository;

    // Gives this service access to financial institutions in the database
    @Autowired
    private FinancialInstitutionRepository financialInstitutionRepository;

    // Used to hash the user's password before saving it
    @Autowired
    private PasswordEncoder passwordEncoder;

    //-------------------------------------------------------------------------------------------------

    // Registers a new user in LimitGuard
    public RegisterResponse registerUser(RegisterRequest registrationDetails) {

        // Check if an account with the same email already exists
        if (userRepository.findByEmail(registrationDetails.getEmail()).isPresent()) {
            // Stop registration if an account already uses this email
            throw new EmailAlreadyExistsException("An account with the same email is already registered");
        }

        // Find the financial institution selected during registration
        FinancialInstitution selectedFinancialInstitution = financialInstitutionRepository
                .findById(registrationDetails.getFinancialInstitutionId())
                .orElseThrow(() -> new RuntimeException("Financial institution not found"));

        // Create a new user
        User newUser = new User();

        // Add the registration details to the new user
        newUser.setFirstName(registrationDetails.getFirstName());
        newUser.setLastName(registrationDetails.getLastName());
        newUser.setEmail(registrationDetails.getEmail());
        newUser.setPassword(passwordEncoder.encode(registrationDetails.getPassword()));

        // Connect the new user to their financial institution
        newUser.setFinancialInstitution(selectedFinancialInstitution);
        User savedUser = userRepository.save(newUser);

        // response that will be sent back after registration
        RegisterResponse registerResponse = new RegisterResponse();

        registerResponse.setId(savedUser.getId());
        registerResponse.setFirstName(savedUser.getFirstName());
        registerResponse.setLastName(savedUser.getLastName());
        registerResponse.setEmail(savedUser.getEmail());
        registerResponse.setRole(savedUser.getRole());
        registerResponse.setStatus(savedUser.getStatus());
        registerResponse.setEmailVerified(savedUser.isEmailVerified());

        return registerResponse;
    }
}
