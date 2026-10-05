package com.example.limitguard.service;

import com.example.limitguard.dto.*;
import com.example.limitguard.exception.*;
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
import com.example.limitguard.model.UserToken;
import com.example.limitguard.repository.UserTokenRepository;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import com.example.limitguard.dto.UpdateUserRoleRequest;
import com.example.limitguard.model.UserRole;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import com.example.limitguard.security.JWTUtils;
import com.example.limitguard.security.MyUserDetails;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import com.example.limitguard.repository.AuditLogRepository;
import com.example.limitguard.model.AuditLog;
import com.example.limitguard.model.UserStatus;
import com.example.limitguard.dto.DeactivateUserRequest;

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

    // Gives this service access to AuditLogRepository
    @Autowired
    private AuditLogRepository auditLogRepository;

    //-------------------------------------------------------------------------------------------------

    // makes sure registration is rolled back if part of the process fails
    @Transactional
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
        // User saved temporarily
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
        // Save the verification token in the database temporarily
        userTokenRepository.save(verificationToken);

        // runtime exception happens here
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

    // creates a password-reset request for a user
    public void forgotPassword(ForgotPasswordRequest forgotPasswordDetails) {

        // find the account using the submitted email
        User userFound = findUserByEmail(forgotPasswordDetails.getEmail());
        // create a new password-reset token
        UserToken passwordResetToken = new UserToken();
        // generate a random unique token
        passwordResetToken.setToken(UUID.randomUUID().toString());
        // mark this token as a password-reset token
        passwordResetToken.setTokenType(UserTokenType.PASSWORD_RESET);
        // the reset token expires after 1 hour
        passwordResetToken.setExpiresAt(LocalDateTime.now().plusHours(1));
        // connect the token to the user
        passwordResetToken.setUser(userFound);
        // save the token
        userTokenRepository.save(passwordResetToken);

        // send the password-reset email
        emailService.SendPasswordResetEmail(
                userFound.getEmail(),
                passwordResetToken.getToken()
        );
    }


    // resets a users password using a valid password-reset token
    public void resetPassword(ResetPasswordRequest resetPasswordDetails) {

        // find the password-reset token
        UserToken passwordResetToken = userTokenRepository
                .findByToken(resetPasswordDetails.getToken())
                .orElseThrow(() ->
                        new InvalidTokenException("Password reset token not found"));

        // make sure this token is specifically for password resets
        if (passwordResetToken.getTokenType() != UserTokenType.PASSWORD_RESET) {
            throw new InvalidTokenException("Invalid password reset token");
        }

        // do not allow a token to be used more than once
        if (passwordResetToken.isUsed()) {
            throw new InvalidTokenException("Password reset token has already been used");
        }

        // do not allow an expired token
        if (passwordResetToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new InvalidTokenException("Password reset token has expired");
        }

        // get the user that belongs to the reset token
        User userToUpdate = passwordResetToken.getUser();
        // hash the new password before storing it
        userToUpdate.setPassword(passwordEncoder.encode(resetPasswordDetails.getNewPassword()));
        // mark the token as used
        passwordResetToken.setUsed(true);
        // save the new password and token status
        userRepository.save(userToUpdate);
        userTokenRepository.save(passwordResetToken);
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

    // changes the password for the currently logged-in user
    public void changePassword(ChangePasswordRequest changePasswordRequest) {

        // gets the email of the user currently logged in from Spring Security
        String email = SecurityContextHolder.getContext().getAuthentication().getName();

        // finds the logged-in user in the database
        User user = findUserByEmail(email);

        // checks if the current password entered matches the stored hashed password
        if (!passwordEncoder.matches(changePasswordRequest.getCurrentPassword(), user.getPassword())) {
            throw new IncorrectPasswordException("Current password is incorrect");
        }

        // hashes the new password before saving it
        String hashedPassword = passwordEncoder.encode(changePasswordRequest.getNewPassword());
        user.setPassword(hashedPassword);

        // saves the user with the new password
        userRepository.save(user);
    }


    // gets the profile of the currently logged-in user
    public UserProfileResponse getMyProfile() {

        // gets the email of the currently logged-in user from Spring Security
        String email = SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();

        // finds that user in the database
        User user = findUserByEmail(email);

        // returns only the information that should be shown in the profile
        return new UserProfileResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getRole(),
                user.getFinancialInstitution(),
                user.getProfilePicturePath()
        );
    }


    // updates the profile of the currently logged-in user
    public UserProfileResponse updateMyProfile(UpdateProfileRequest updateProfileRequest) {

        // gets the email of the currently logged-in user from Spring Security
        String email = SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();

        // finds that user in the database
        User user = findUserByEmail(email);

        // checks that the first name is not blank if the user wants to update it
        if (updateProfileRequest.getFirstName() != null) {

            if (updateProfileRequest.getFirstName().isBlank()) {
                throw new IllegalArgumentException("First name cannot be blank");
            }

            user.setFirstName(updateProfileRequest.getFirstName());
        }

        // checks that the last name is not blank if the user wants to update it
        if (updateProfileRequest.getLastName() != null) {

            if (updateProfileRequest.getLastName().isBlank()) {
                throw new IllegalArgumentException("Last name cannot be blank");
            }

            user.setLastName(updateProfileRequest.getLastName());
        }

        // updates the email only if the user provided a new one
        if (updateProfileRequest.getEmail() != null) {

            // makes sure the email is not blank
            if (updateProfileRequest.getEmail().isBlank()) {
                throw new IllegalArgumentException("Email cannot be blank");
            }

            // only checks for duplicates if the user is actually changing their email
            if (!updateProfileRequest.getEmail().equalsIgnoreCase(user.getEmail())) {

                // is there already a user with this new email?
                Optional<User> existingUser =
                        userRepository.findByEmail(updateProfileRequest.getEmail());

                // prevents the user from using an email that belongs to another account
                if (existingUser.isPresent()) {
                    throw new EmailAlreadyExistsException("Email already exists");
                }

                user.setEmail(updateProfileRequest.getEmail());
            }
        }

        // saves the updated information
        User updatedUser = userRepository.save(user);

        // returns the updated profile
        return new UserProfileResponse(
                updatedUser.getId(),
                updatedUser.getFirstName(),
                updatedUser.getLastName(),
                updatedUser.getEmail(),
                updatedUser.getRole(),
                updatedUser.getFinancialInstitution(),
                user.getProfilePicturePath()
        );
    }

    // uploads a profile picture for the currently logged-in user
    public UserProfileResponse uploadProfilePicture(MultipartFile image) throws IOException {
        // gets the email of the currently logged-in user from Spring Security
        String email = SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();

        // finds the logged-in user in the database
        User user = findUserByEmail(email);

        // makes sure an image was actually uploaded
        if (image.isEmpty()) {
            throw new IllegalArgumentException("Profile picture cannot be empty");
        }

        // getting the image filename and storing it in filename
        String filename = image.getOriginalFilename();

        // choosing where the uploaded profile pictures will be stored
        Path uploadPath = Paths.get("uploads");

        // if the uploads folder does not exist, create it
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        // combines the uploads folder with the image filename
        // uploads/ + profile.jpg -> uploads/profile.jpg
        Path filePath = uploadPath.resolve(filename);

        // saves the uploaded image inside the uploads folder
        Files.write(filePath, image.getBytes());

        // saves the image location in the logged-in users profile
        user.setProfilePicturePath(filePath.toString());

        // saves the updated user in the database
        User updatedUser = userRepository.save(user);

        // returns the users updated profile
        return new UserProfileResponse(
                updatedUser.getId(),
                updatedUser.getFirstName(),
                updatedUser.getLastName(),
                updatedUser.getEmail(),
                updatedUser.getRole(),
                updatedUser.getFinancialInstitution(),
                user.getProfilePicturePath()
        );
    }



    // makes sure the user deactivation and audit log are saved together
    @Transactional
    // allows an admin to deactivate another user account
    public void deactivateUser(Long userId, DeactivateUserRequest deactivateUserRequest) {
        // gets the currently logged-in admins email
        String email = SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();

        // finds the admin in the database
        User admin = findUserByEmail(email);

        // finds the user that the admin wants to deactivate
        // if the user does not exist, throw UserNotFoundException
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        // prevents an admin from deactivating their own account
        if (admin.getId().equals(user.getId())) {
            throw new IllegalArgumentException("Admin cannot deactivate their own account");
        }

        // changes the account status instead of deleting the user
        user.setStatus(UserStatus.DEACTIVATED);

        // saves the updated user
        userRepository.save(user);

        // creates an audit record for the deactivation
        AuditLog auditLog = new AuditLog();

        auditLog.setAction("USER_DEACTIVATED");
        auditLog.setEntityType("USER");
        auditLog.setEntityId(user.getId());
        auditLog.setDetails(deactivateUserRequest.getReason());
        auditLog.setActor(admin);

        // saves the audit record
        auditLogRepository.save(auditLog);
    }


    // allows an admin to change another users role
    @Transactional
    public void updateUserRole(Long userId, UpdateUserRoleRequest updateUserRoleRequest) {

        // gets the currently logged-in admins email
        String email = SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();

        // finds the admin in the database
        User admin = findUserByEmail(email);

        // finds the user whose role the admin wants to change
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        // prevents the admin from changing their own role
        if (admin.getId().equals(user.getId())) {
            throw new IllegalArgumentException("You cannot change your own role");
        }

        // prevents admin from being assigned through this endpoint
        if (updateUserRoleRequest.getRole() == UserRole.ADMIN) {
            throw new IllegalArgumentException("ADMIN role cannot be assigned");
        }

        // changes the users role
        user.setRole(updateUserRoleRequest.getRole());

        // saves the updated user
        userRepository.save(user);

        // records the role change in the audit log
        AuditLog auditLog = new AuditLog();

        auditLog.setAction("USER_ROLE_UPDATED");
        auditLog.setEntityType("USER");
        auditLog.setEntityId(user.getId());
        auditLog.setDetails("Role changed to: " + updateUserRoleRequest.getRole());
        auditLog.setActor(admin);

        auditLogRepository.save(auditLog);

    }
}
