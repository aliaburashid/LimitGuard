package com.example.limitguard.service;

import com.example.limitguard.dto.*;
import com.example.limitguard.exception.*;
import com.example.limitguard.model.FinancialInstitution;
import com.example.limitguard.model.User;
import com.example.limitguard.enums.UserTokenType;
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
import com.example.limitguard.enums.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import com.example.limitguard.enums.UserStatus;
import com.example.limitguard.dto.DeactivateUserRequest;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.StandardOpenOption;

// Class that contains business logic
// Create and manage an object of this class for me
@Service
public class UserService {

    // Logs important authentication and security events
    // create a logger and label these messages as coming from UserService
    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

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

    // records user changes in the audit log without repeating the same code
    private void recordAuditLog(String action, User user, String details, User actor) {
        AuditLog auditLog = new AuditLog();
        auditLog.setAction(action);
        auditLog.setEntityType("USER");
        auditLog.setEntityId(user.getId());
        auditLog.setDetails(details);
        auditLog.setActor(actor);

        // saves the audit log in the database
        auditLogRepository.save(auditLog);
    }


    // makes sure registration is rolled back if part of the process fails
    @Transactional
    // Registers a new user in LimitGuard
    public RegisterResponse registerUser(RegisterRequest registrationDetails) {

        // Check if an account with the same email already exists
        if (userRepository.findByEmail(registrationDetails.getEmail()).isPresent()) {

            // Log the rejected registration without logging sensitive information
            logger.warn("Registration rejected because an account with this email already exists");

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

        // Log successful registration without logging the verification token
        logger.info("User ID {} registered successfully", savedUser.getId());

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

        // Log successful email verification without logging the verification token
        logger.info("Email verified successfully for user ID {}", userToVerify.getId());
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

        // Log the password-reset request without logging the reset token
        logger.info("Password reset requested for user ID {}", userFound.getId());
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

        // Log successful password reset without logging the password or reset token
        logger.info("Password reset successfully for user ID {}", userToUpdate.getId());
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
            // Log the rejected login attempt without logging passwords or tokens
            logger.warn("Login blocked because email is not verified for user ID {}", userFound.getId());
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

        // Log the successful authentication without logging the JWT
        logger.info("User ID {} logged in successfully", userFound.getId());

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

            // Log the rejected password change without logging either password
            logger.warn("Password change rejected because current password was incorrect for user ID {}", user.getId());

            throw new IncorrectPasswordException("Current password is incorrect");
        }

        // hashes the new password before saving it
        String hashedPassword = passwordEncoder.encode(changePasswordRequest.getNewPassword());
        user.setPassword(hashedPassword);

        // saves the user with the new password
        userRepository.save(user);

        // Log the successful password change
        logger.info("Password changed successfully for user ID {}", user.getId());
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
    // MultipartFile contains the image uploaded by the user
    // throws IOException because reading or saving a file can fail
    public UserProfileResponse uploadProfilePicture(MultipartFile image) throws IOException {

        // Spring Security stores information about the currently logged-in user
        // getContext() gets the current security information
        // getAuthentication() gets the logged-in user's authentication details
        // getName() gets the user's email address
        String email = SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();

        // find the logged-in user in the database using their email
        User user = findUserByEmail(email);

        // if no image provided
        if (image == null || image.isEmpty()) {

            // stop the method and return an error for an empty upload
            throw new IllegalArgumentException("Profile picture cannot be empty");
        }

        // set the maximum allowed image size to 5 MB
        // 5L means the number 5 is stored as a long
        // 1024 bytes = 1 KB, and 1024 KB = 1 MB
        long maxFileSize = 5L * 1024 * 1024;

        // getSize() returns the uploaded file size in bytes
        // compare the uploaded file size with our maximum allowed size
        if (image.getSize() > maxFileSize) {

            // reject the upload if the image is larger than 5 MB
            throw new IllegalArgumentException("Profile picture cannot exceed 5 MB");
        }

        // getContentType() gets the image type declared by the upload request
        // examples: image/jpeg, image/png, application/pdf
        // this value comes from the client, so we cannot fully trust it
        String contentType = image.getContentType();

        // only allow JPEG and PNG content types
        if (!"image/jpeg".equals(contentType) &&
                !"image/png".equals(contentType)) {

            // reject files that do not declare an allowed image type
            throw new IllegalArgumentException("Only JPEG and PNG images are allowed");
        }

        // getBytes() reads the uploaded file into a byte array
        // a byte array stores the actual binary data of the image
        byte[] imageBytes = image.getBytes();

        // create a variable to store the real image format
        // null means we have not identified the format yet
        String imageFormat = null;

        // ByteArrayInputStream allows Java to read the byte array like a file
        // ImageIO.createImageInputStream() prepares the data for image reading
        // try-with-resources automatically closes the input when finished
        try (var input = ImageIO.createImageInputStream(
                new ByteArrayInputStream(imageBytes))) {

            // make sure Java successfully created an image input stream
            if (input != null) {

                // find image readers that understand the uploaded file's data
                // Java uses the file content to identify possible image formats
                var readers = ImageIO.getImageReaders(input);

                // hasNext() checks whether a suitable image reader exists
                if (readers.hasNext()) {

                    // next() gets the first suitable image reader
                    var reader = readers.next();

                    try {

                        // tell the reader which image data it should examine
                        reader.setInput(input);

                        // getFormatName() gets the detected format
                        // examples: JPEG or PNG
                        imageFormat = reader.getFormatName();

                    } finally {

                        // dispose() releases resources used by the image reader
                        // finally runs even if an error occurs while reading
                        reader.dispose();
                    }
                }
            }
        }

        // equalsIgnoreCase() compares text without caring about uppercase or lowercase
        // isJpeg becomes true if the detected format is JPEG
        boolean isJpeg = "JPEG".equalsIgnoreCase(imageFormat);

        // isPng becomes true if the detected format is PNG
        boolean isPng = "PNG".equalsIgnoreCase(imageFormat);

        // reject the file if it is neither JPEG nor PNG
        // also reject it if the detected format does not match the declared content type
        // this prevents someone from simply claiming a different file is an image
        if ((!isJpeg && !isPng) ||
                (isJpeg && !"image/jpeg".equals(contentType)) ||
                (isPng && !"image/png".equals(contentType))) {

            // stop the upload because the image format is invalid
            throw new IllegalArgumentException("Invalid image format");
        }

        // ImageIO.read() tries to decode the actual image data
        // BufferedImage stores the decoded image in memory
        // this checks more than just the file extension or declared content type
        BufferedImage decodedImage = ImageIO.read(
                new ByteArrayInputStream(imageBytes)
        );

        // ImageIO.read() returns null if it cannot decode the file as an image
        if (decodedImage == null) {

            // reject files that Java cannot decode
            throw new IllegalArgumentException("Invalid or corrupted image");
        }

        // choose the file extension based on the detected image format
        // ? : is the ternary operator, which is a shorter way of writing if-else
        // if isJpeg is true, use .jpg; otherwise use .png
        String extension = isJpeg ? ".jpg" : ".png";

        // UUID.randomUUID() generates a random unique identifier
        // we use this instead of the original filename provided by the user
        // example: 8b7c2d91-4e12-4a83-9c25-33d8f0a1b654.jpg
        // this prevents users with the same original filename from overwriting each other
        String filename = UUID.randomUUID() + extension;

        // Paths.get("uploads") selects the folder where images will be stored
        // toAbsolutePath() converts it into a full filesystem path
        // normalize() removes unnecessary parts such as "." and ".."
        Path uploadPath = Paths.get("uploads").toAbsolutePath().normalize();

        // create the uploads folder if it does not already exist
        // createDirectories() also works when the folder already exists
        Files.createDirectories(uploadPath);

        // resolve(filename) combines the uploads folder with our generated filename
        // normalize() cleans up the resulting path
        // example: /project/uploads/random-id.jpg
        Path filePath = uploadPath.resolve(filename).normalize();

        // getParent() gets the folder containing the new image
        // equals(uploadPath) checks that this is our intended uploads folder
        // this prevents the filename from escaping the uploads directory
        if (!filePath.getParent().equals(uploadPath)) {

            // reject the file if its path is outside the expected folder
            throw new IllegalArgumentException("Invalid image file path");
        }

        // Files.write() saves the image bytes to the selected path
        // CREATE_NEW creates a new file and fails if that filename already exists
        // this prevents accidentally overwriting an existing image
        Files.write(filePath, imageBytes, StandardOpenOption.CREATE_NEW);

        // store the image location in the user's profile
        // Paths.get("uploads", filename) creates a relative path
        // toString() converts the Path into text that can be saved in the database
        // example: uploads/random-id.jpg
        user.setProfilePicturePath(
                Paths.get("uploads", filename).toString()
        );

        // save the user's updated profile picture path in PostgreSQL
        // save() returns the saved User object
        User updatedUser = userRepository.save(user);

        // create the response that will be returned to the frontend
        // include the user's ID, name, email, role, institution and picture path
        return new UserProfileResponse(

                // the saved user's database ID
                updatedUser.getId(),

                // the saved user's first name
                updatedUser.getFirstName(),

                // the saved user's last name
                updatedUser.getLastName(),

                // the saved user's email
                updatedUser.getEmail(),

                // the saved user's role
                updatedUser.getRole(),

                // the financial institution the user belongs to
                updatedUser.getFinancialInstitution(),

                // the location of the uploaded profile picture
                updatedUser.getProfilePicturePath()
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

            // Log the rejected security operation
            logger.warn("Admin user ID {} attempted to deactivate their own account", admin.getId());

            throw new IllegalArgumentException("Admin cannot deactivate their own account");
        }

        // changes the account status instead of deleting the user
        user.setStatus(UserStatus.DEACTIVATED);

        // saves the updated user
        userRepository.save(user);

        // creates an audit record for the deactivation
        recordAuditLog("USER_DEACTIVATED", user, deactivateUserRequest.getReason(), admin);

        // Log the successful account deactivation
        logger.info("User ID {} was deactivated by admin user ID {}", user.getId(), admin.getId());
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

            // Log the rejected security operation
            logger.warn("Admin user ID {} attempted to change their own role", admin.getId());

            throw new IllegalArgumentException("You cannot change your own role");
        }

        // prevents admin from being assigned through this endpoint
        if (updateUserRoleRequest.getRole() == UserRole.ADMIN) {

            // Log the rejected privileged-role assignment
            logger.warn("Admin user ID {} attempted to assign ADMIN role to user ID {}", admin.getId(), user.getId());

            throw new IllegalArgumentException("ADMIN role cannot be assigned");
        }

        // changes the users role
        user.setRole(updateUserRoleRequest.getRole());

        // saves the updated user
        userRepository.save(user);

        // records the role change in the audit log
        recordAuditLog("USER_ROLE_UPDATED", user, "Role changed to: " + updateUserRoleRequest.getRole(), admin);

        // Log the successful role change
        logger.info("User ID {} role changed to {} by admin user ID {}", user.getId(), user.getRole(), admin.getId());

    }
}
