package com.example.authservice.exception;

import com.example.authservice.controller.FinancialInstitutionController;
import com.example.authservice.controller.UserController;
import com.example.authservice.security.JWTUtils;
import com.example.authservice.security.MyUserDetailsService;
import com.example.authservice.security.SecurityConfiguration;
import com.example.authservice.service.FinancialInstitutionService;
import com.example.authservice.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.io.IOException;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = {UserController.class, FinancialInstitutionController.class},
        properties = {"jwt-secret=auth-exception-tests-only-signing-key", "jwt-expiration-ms=60000"}
)
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ImportAutoConfiguration(AopAutoConfiguration.class)
@Import({SecurityConfiguration.class, JWTUtils.class})
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private FinancialInstitutionService financialInstitutionService;

    @MockitoBean
    private MyUserDetailsService myUserDetailsService;

    @Test
    void rejectsInvalidRequestBodyWithFieldErrors() throws Exception {
        String path = "/api/auth/users/login";
        expectError(mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{}")),
                HttpStatus.BAD_REQUEST, path)
                .andExpect(jsonPath("$.message", containsString("email: Email is required")))
                .andExpect(jsonPath("$.message", containsString("password: Password is required")));
    }

    @ParameterizedTest
    @MethodSource("loginFailures")
    void mapsLoginFailures(LoginFailure failure) throws Exception {
        String path = "/api/auth/users/login";
        when(userService.loginUser(any())).thenThrow(failure.exception());

        expectError(mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"user@example.test","password":"test-input"}
                                """)), failure.status(), path)
                .andExpect(jsonPath("$.message").value(failure.message()));
    }

    private static Stream<LoginFailure> loginFailures() {
        return Stream.of(
                new LoginFailure(new BadCredentialsException("Private detail"),
                        HttpStatus.UNAUTHORIZED, "Invalid email or password"),
                new LoginFailure(new UsernameNotFoundException("Private detail"),
                        HttpStatus.UNAUTHORIZED, "Invalid email or password"),
                new LoginFailure(new EmailNotVerifiedException("Please verify your email before logging in"),
                        HttpStatus.FORBIDDEN, "Please verify your email before logging in"),
                new LoginFailure(new DisabledException("Private detail"),
                        HttpStatus.FORBIDDEN, "This account has been deactivated")
        );
    }

    private record LoginFailure(RuntimeException exception, HttpStatus status, String message) {}

    @Test
    void rejectsInvalidVerificationToken() throws Exception {
        String path = "/api/auth/users/verify-email";
        doThrow(new InvalidTokenException("Verification token has expired"))
                .when(userService).verifyEmail("test-token");

        expectError(mvc.perform(get(path).param("token", "test-token")), HttpStatus.BAD_REQUEST, path)
                .andExpect(jsonPath("$.message").value("Verification token has expired"));
    }

    @Test
    @WithMockUser
    void rejectsIncorrectCurrentPassword() throws Exception {
        String path = "/api/auth/users/change-password";
        doThrow(new IncorrectPasswordException("Current password is incorrect"))
                .when(userService).changePassword(any());

        expectError(mvc.perform(patch(path).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"test-input","newPassword":"Test-only1!"}
                                """)), HttpStatus.BAD_REQUEST, path)
                .andExpect(jsonPath("$.message").value("Current password is incorrect"));
    }

    @Test
    @WithMockUser
    void reportsDuplicateEmail() throws Exception {
        String path = "/api/auth/users/profile";
        when(userService.updateMyProfile(any())).thenThrow(new EmailAlreadyExistsException("Email already exists"));

        expectError(mvc.perform(patch(path).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"user@example.test"}
                                """)), HttpStatus.CONFLICT, path)
                .andExpect(jsonPath("$.message").value("Email already exists"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void reportsDuplicateInstitution() throws Exception {
        String path = "/api/financial-institutions";
        when(financialInstitutionService.createFinancialInstitution(any()))
                .thenThrow(new FinancialInstitutionAlreadyExistsException("Financial institution name already exists"));

        expectError(mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Test institution","status":"ACTIVE"}
                                """)), HttpStatus.CONFLICT, path)
                .andExpect(jsonPath("$.message").value("Financial institution name already exists"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void reportsMissingUser() throws Exception {
        String path = "/api/auth/users/123/deactivate";
        doThrow(new UserNotFoundException("User not found")).when(userService).deactivateUser(any(), any());

        expectError(mvc.perform(patch(path).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"Test reason"}
                                """)), HttpStatus.NOT_FOUND, path)
                .andExpect(jsonPath("$.message").value("User not found"));
    }

    @Test
    void reportsMissingInstitution() throws Exception {
        String path = "/api/auth/users/register";
        when(userService.registerUser(any()))
                .thenThrow(new FinancialInstitutionNotFoundException("Financial institution not found"));

        expectError(mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Test","lastName":"User","email":"user@example.test",
                                 "password":"Test-only1!","financialInstitutionId":123}
                                """)), HttpStatus.NOT_FOUND, path)
                .andExpect(jsonPath("$.message").value("Financial institution not found"));
    }

    @Test
    @WithMockUser
    void rejectsInvalidImage() throws Exception {
        when(userService.uploadProfilePicture(any())).thenThrow(new IllegalArgumentException("Invalid image format"));

        expectError(uploadPicture(), HttpStatus.BAD_REQUEST, "/api/auth/users/profile-picture")
                .andExpect(jsonPath("$.message").value("Invalid image format"));
    }

    @Test
    @WithMockUser
    void hidesInternalImageStorageFailure() throws Exception {
        when(userService.uploadProfilePicture(any())).thenThrow(new IOException("Private filesystem detail"));

        expectError(uploadPicture(), HttpStatus.INTERNAL_SERVER_ERROR, "/api/auth/users/profile-picture")
                .andExpect(jsonPath("$.message").value("Unable to save profile picture"));
    }

    @Test
    void hidesInternalMailFailure() throws Exception {
        String path = "/api/auth/users/forgot-password";
        doThrow(new MailSendException("Private mail detail")).when(userService).forgotPassword(any());

        expectError(mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"user@example.test"}
                                """)), HttpStatus.INTERNAL_SERVER_ERROR, path)
                .andExpect(jsonPath("$.message").value("Unable to send email"));
    }

    @Test
    void preservesUnauthorizedResponseForMissingJwt() throws Exception {
        expectUnauthorized(mvc.perform(get("/api/auth/users/profile")));
    }

    @Test
    void preservesUnauthorizedResponseForInvalidJwt() throws Exception {
        expectUnauthorized(mvc.perform(get("/api/auth/users/profile").header("Authorization", "Bearer invalid")));
    }

    @Test
    @WithMockUser(roles = "RELATIONSHIP_MANAGER")
    void preservesForbiddenResponseForNonAdmin() throws Exception {
        mvc.perform(patch("/api/auth/users/123/deactivate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"Test reason"}
                                """))
                .andExpect(status().isForbidden());
    }

    private ResultActions uploadPicture() throws Exception {
        MockMultipartFile image = new MockMultipartFile("image", "test.png", "image/png", new byte[]{1});
        return mvc.perform(multipart(HttpMethod.PATCH, "/api/auth/users/profile-picture").file(image));
    }

    private ResultActions expectError(ResultActions result, HttpStatus expectedStatus, String path) throws Exception {
        return result.andExpect(status().is(expectedStatus.value()))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(expectedStatus.value()))
                .andExpect(jsonPath("$.error").value(expectedStatus.name()))
                .andExpect(jsonPath("$.path").value(path));
    }

    private void expectUnauthorized(ResultActions result) throws Exception {
        result.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Authentication is required"));
    }
}
