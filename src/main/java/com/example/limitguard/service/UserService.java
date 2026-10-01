package com.example.limitguard.service;

import com.example.limitguard.dto.LoginRequest;
import com.example.limitguard.dto.RegisterRequest;
import com.example.limitguard.model.FinancialInstitution;
import com.example.limitguard.model.User;
import com.example.limitguard.model.UserTokenType;
import com.example.limitguard.repository.FinancialInstitutionRepository;
import com.example.limitguard.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.example.limitguard.dto.RegisterResponse;
import com.example.limitguard.exception.EmailAlreadyExistsException;
import com.example.limitguard.model.UserToken;
import com.example.limitguard.repository.UserTokenRepository;
import com.example.limitguard.exception.InvalidTokenException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import com.example.limitguard.exception.EmailNotVerifiedException;
import java.time.LocalDateTime;
import java.util.UUID;
import com.example.limitguard.security.JWTUtils;
import com.example.limitguard.security.MyUserDetails;
import com.example.limitguard.dto.LoginResponse;
import com.example.limitguard.exception.FinancialInstitutionNotFoundException;

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

    // Gives this service access to user tokens in the database
    @Autowired
    private UserTokenRepository userTokenRepository;

    // Gives this service access to the email service
    @Autowired
    private EmailService emailService;

    // Used to hash the user's password before saving it
    @Autowired
    private PasswordEncoder passwordEncoder;

    // Gives the service access to Spring Security's authentication system
    @Autowired
    private AuthenticationManager authenticationManager;

    // Gives this service access to the JWT methods
    @Autowired
    private JWTUtils jwtUtils;

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
                .orElseThrow(() -> new FinancialInstitutionNotFoundException("Financial institution not found"));

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

        // Create a new token for email verification
        UserToken verificationToken = new UserToken();
        // Generate a random unique difficult-to-guess token
        verificationToken.setToken(UUID.randomUUID().toString());
        // This token will be used to verify the user's email
        verificationToken.setTokenType(UserTokenType.EMAIL_VERIFICATION);
        // The verification link will expire after 24 hours
        verificationToken.setExpiresAt(LocalDateTime.now().plusHours(24));
        // Connect the token to the newly registered user
        verificationToken.setUser(savedUser);
        // Save the verification token in the database
        userTokenRepository.save(verificationToken);

        // send the verification link to the users email
        emailService.SendVerificationEmail(savedUser.getEmail(), verificationToken.getToken());

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


    // Verifies a users email using their verification token
    public void verifyEmail(String token) {

        // Find the verification token in the database
        UserToken verificationToken = userTokenRepository
                .findByToken(token)
                .orElseThrow(() -> new InvalidTokenException("Verification token not found"));

        // make sure this token is specifically for email verification
        if (verificationToken.getTokenType() != UserTokenType.EMAIL_VERIFICATION) {
            throw new InvalidTokenException(
                    "Invalid email verification token"
            );
        }

        // Check if the token has already been used
        if (verificationToken.isUsed()) {
            throw new InvalidTokenException(
                    "Verification token has already been used"
            );
        }

        // Check if the token has expired
        if (verificationToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new InvalidTokenException(
                    "Verification token has expired"
            );
        }

        // Check if the token has already been used
        if (verificationToken.isUsed()) {
            throw new InvalidTokenException("Verification token has already been used");
        }

        // Get the user that belongs to this token
        User userToVerify = verificationToken.getUser();

        // Mark the users email as verified
        userToVerify.setEmailVerified(true);

        // Mark the token as used so it cannot be used again
        verificationToken.setUsed(true);

        // Save the changes
        userRepository.save(userToVerify);
        userTokenRepository.save(verificationToken);
    }

    // Finds a user by their email address
    public User findUserByEmail(String email) {
        // Search for the user in the database
        return userRepository.findByEmail(email)
                // exception from spring security
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    // Logs a user into LimitGuard
    public LoginResponse loginUser(LoginRequest loginDetails) {
        // Find the user using the email they entered
        User userFound = findUserByEmail(loginDetails.getEmail());

        // Do not allow the user to log in until their email is verified
        if (!userFound.isEmailVerified()) {
            throw new EmailNotVerifiedException("Please verify your email before logging in");
        }

        // Ask Spring Security to check the email and password
        // Basically saying: Spring, here is the email and password the person entered. Please authenticate them.
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginDetails.getEmail(),
                        loginDetails.getPassword()
                )
        );

        // Store the authenticated user in Spring Security
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // Get the authenticated user's details
        MyUserDetails userDetails = (MyUserDetails) authentication.getPrincipal();

        // Generate a JWT token for the logged-in user
        String jwtToken = jwtUtils.generateJwtToken(userDetails);

        // Return the JWT inside the login response
        return new LoginResponse(jwtToken);
    }
}
