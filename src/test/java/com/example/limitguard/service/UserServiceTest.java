
package com.example.limitguard.service;

import com.example.limitguard.model.User;
import com.example.limitguard.repository.UserRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

// tells JUnit to use Mockito to create fake objects for our tests
@ExtendWith(MockitoExtension.class)
public class UserServiceTest {

    // creates a fake UserRepository
    // this prevents our test from accessing the real PostgreSQL database
    @Mock
    private UserRepository userRepository;

    // creates UserService and automatically adds the fake repository
    @InjectMocks
    private UserService userService;

    // runs before each test
    @BeforeEach
    void setUp() {

        // creates a fake logged-in user with this email
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        "test@example.com",
                        null
                );

        // stores the fake logged-in user in Spring Security
        // our UserService can now get the email using getAuthentication().getName()
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    // runs after each test
    @AfterEach
    void tearDown() {

        // removes the fake logged-in user
        // this prevents authentication information from affecting other tests
        SecurityContextHolder.clearContext();
    }

    // checks that the application rejects an empty profile picture
    @Test
    void shouldRejectEmptyProfilePicture() throws Exception {

        // creates a fake user object
        User user = new User();

        // gives the fake user an email address
        user.setEmail("test@example.com");

        // tells Mockito what to return when the service searches for the user
        // Optional.of(user) means that a matching user was found
        when(userRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.of(user));

        // creates a fake uploaded image
        // "image" is the form field name
        // "profile.jpg" is the original filename
        // "image/jpeg" is the declared content type
        // new byte[0] means the file is empty
        MockMultipartFile image = new MockMultipartFile(
                "image",
                "profile.jpg",
                "image/jpeg",
                new byte[0]
        );

        // assertThrows checks whether the method throws the expected exception
        // we expect IllegalArgumentException because empty images are not allowed
        assertThrows(IllegalArgumentException.class, () -> {

            // tries to upload the empty image
            userService.uploadProfilePicture(image);
        });
    }

    // checks that users cannot upload profile pictures larger than 5 MB
    @Test
    void shouldRejectOversizedProfilePicture() throws Exception {

        // create a fake user
        User user = new User();
        user.setEmail("test@example.com");

        // tell Mockito to return our fake user instead of accessing PostgreSQL
        when(userRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.of(user));

        // create a fake image larger than 5 MB
        byte[] largeImage = new byte[5 * 1024 * 1024 + 1];

        // simulate uploading the oversized image
        MockMultipartFile image = new MockMultipartFile(
                "image",
                "large-profile.jpg",
                "image/jpeg",
                largeImage
        );

        // verify that the upload is rejected
        assertThrows(IllegalArgumentException.class, () -> {
            userService.uploadProfilePicture(image);
        });
    }

    // checks that users cannot upload unsupported file types
    @Test
    void shouldRejectInvalidFileType() throws Exception {

        // create a fake user
        User user = new User();
        user.setEmail("test@example.com");

        // return the fake user instead of accessing PostgreSQL
        when(userRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.of(user));

        // create fake PDF content
        byte[] pdfBytes = "This is a PDF file".getBytes();

        // create a fake uploaded PDF
        MockMultipartFile image = new MockMultipartFile(
                "image",
                "document.pdf",
                "application/pdf",
                pdfBytes
        );

        // check that the service rejects the PDF upload
        assertThrows(IllegalArgumentException.class, () -> {
            userService.uploadProfilePicture(image);
        });
    }

    // checks that a fake JPEG image is rejected
    @Test
    void shouldRejectFakeJpegImage() throws Exception {

        // create a fake user
        User user = new User();
        user.setEmail("test@example.com");

        // return the fake user without accessing PostgreSQL
        when(userRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.of(user));

        // create text instead of real image data
        byte[] fakeImage = "This is not a real JPEG image".getBytes();

        // pretend the text file is a JPEG image
        MockMultipartFile image = new MockMultipartFile(
                "image",
                "profile.jpg",
                "image/jpeg",
                fakeImage
        );

        // check that the service rejects the fake image
        assertThrows(IllegalArgumentException.class, () -> {
            userService.uploadProfilePicture(image);
        });
    }
}
