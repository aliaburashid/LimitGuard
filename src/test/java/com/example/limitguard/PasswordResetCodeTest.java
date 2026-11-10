package com.example.limitguard;

import com.example.limitguard.dto.ResetPasswordRequest;
import com.example.limitguard.dto.VerificationCodeResponse;
import com.example.limitguard.enums.FinancialInstitutionStatus;
import com.example.limitguard.enums.UserRole;
import com.example.limitguard.model.FinancialInstitution;
import com.example.limitguard.model.PasswordResetCode;
import com.example.limitguard.model.User;
import com.example.limitguard.repository.*;
import com.example.limitguard.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// uses an isolated test database and a fake email sender
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PasswordResetCodeTest {

    @Autowired
    private PasswordResetCodeService passwordResetCodeService;

    @Autowired
    private PasswordResetCodeRepository passwordResetCodeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserTokenRepository userTokenRepository;

    @Autowired
    private FinancialInstitutionRepository financialInstitutionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserService userService;

    @Autowired
    private EmailCodeSupport emailCodeSupport;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EmailService emailService;
    private User user;

    @BeforeEach
    void setUp() {
        FinancialInstitution financialInstitution = new FinancialInstitution();
        financialInstitution.setName("Password Recovery Test Institution");
        financialInstitution.setStatus(FinancialInstitutionStatus.ACTIVE);
        financialInstitutionRepository.saveAndFlush(financialInstitution);

        user = new User();
        user.setFirstName("Alex");
        user.setLastName("Morgan");
        user.setEmail("password-recovery@example.test");
        user.setPassword(passwordEncoder.encode("Original-Password1!"));
        user.setEmailVerified(true);
        user.setFinancialInstitution(financialInstitution);
        userRepository.saveAndFlush(user);
    }

    private String latestCode() {
        ArgumentCaptor<String> verificationCode = ArgumentCaptor.forClass(String.class);
        verify(emailService, atLeastOnce()).SendPasswordResetCodeEmail(anyString(), verificationCode.capture());
        return verificationCode.getValue();
    }

    private PasswordResetCode challenge(VerificationCodeResponse response) {
        return passwordResetCodeRepository.findByChallengeId(response.getChallengeId()).orElseThrow();
    }

    @Test
    void sendsOnlyTheCodeAndDoesNotIssueAResetTokenBeforeVerification() {
        VerificationCodeResponse response = passwordResetCodeService.requestCode(user.getEmail());
        String verificationCode = latestCode();
        assertTrue(verificationCode.matches("[0-9]{6}"));
        assertNotEquals(verificationCode, challenge(response).getCodeDigest());
        assertEquals(0, userTokenRepository.count());
        assertFalse(emailCodeSupport.matches("email-code", response.getChallengeId(), verificationCode,
                challenge(response).getCodeDigest()));
        verify(emailService, never()).SendPasswordResetEmail(anyString(), anyString());
    }

    @Test
    void verifiesOnceAndResetsThePasswordOnce() throws Exception {
        VerificationCodeResponse response = passwordResetCodeService.requestCode(user.getEmail());
        String verificationCode = latestCode();
        String resetToken = passwordResetCodeService.verifyCode(response.getChallengeId(), verificationCode).orElseThrow();
        assertTrue(passwordEncoder.matches("Original-Password1!", user.getPassword()));
        assertTrue(passwordResetCodeService.verifyCode(response.getChallengeId(), verificationCode).isEmpty());

        String resetBody = "{\"token\":\"" + resetToken + "\",\"newPassword\":\"Updated-Password2!\"}";
        mockMvc.perform(post("/api/auth/users/reset-password")
                        .contentType(MediaType.APPLICATION_JSON).content(resetBody))
                .andExpect(status().isOk());
        assertTrue(passwordEncoder.matches("Updated-Password2!", user.getPassword()));
        assertEquals(UserRole.RELATIONSHIP_MANAGER, user.getRole());
        assertTrue(user.isEmailVerified());
        mockMvc.perform(post("/api/auth/users/reset-password")
                        .contentType(MediaType.APPLICATION_JSON).content(resetBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void savesFiveWrongAttemptsAndBlocksTheCorrectCodeAfterwards() {
        VerificationCodeResponse response = passwordResetCodeService.requestCode(user.getEmail());
        String correctCode = latestCode();
        String incorrectCode = correctCode.equals("000000") ? "111111" : "000000";
        for (int attempt = 0; attempt < 5; attempt++) {
            assertTrue(passwordResetCodeService.verifyCode(response.getChallengeId(), incorrectCode).isEmpty());
        }
        passwordResetCodeRepository.flush();
        assertEquals(5, challenge(response).getAttempts());
        assertTrue(passwordResetCodeService.verifyCode(response.getChallengeId(), correctCode).isEmpty());
        assertEquals(0, userTokenRepository.count());
    }

    @Test
    void rejectsExpiredCodesAndExpiredResetTokens() {
        VerificationCodeResponse response = passwordResetCodeService.requestCode(user.getEmail());
        String verificationCode = latestCode();
        challenge(response).setExpiresAt(Instant.now().minusSeconds(1));
        assertTrue(passwordResetCodeService.verifyCode(response.getChallengeId(), verificationCode).isEmpty());

        challenge(response).setResendAfter(Instant.now().minusSeconds(1));
        passwordResetCodeService.resendCode(response.getChallengeId());
        String resetToken = passwordResetCodeService.verifyCode(response.getChallengeId(), latestCode()).orElseThrow();
        var savedResetToken = userTokenRepository.findByToken(resetToken).orElseThrow();
        savedResetToken.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        userTokenRepository.flush();
        ResetPasswordRequest resetPasswordRequest = new ResetPasswordRequest();
        resetPasswordRequest.setToken(resetToken);
        resetPasswordRequest.setNewPassword("Updated-Password2!");
        assertThrows(com.example.limitguard.exception.InvalidTokenException.class,
                () -> userService.resetPassword(resetPasswordRequest));
        assertTrue(passwordEncoder.matches("Original-Password1!", user.getPassword()));
    }

    @Test
    void respectsCooldownAndReplacesTheOldCodeOnResend() {
        VerificationCodeResponse response = passwordResetCodeService.requestCode(user.getEmail());
        String oldCode = latestCode();
        assertThrows(ResponseStatusException.class, () -> passwordResetCodeService.resendCode(response.getChallengeId()));
        passwordResetCodeService.requestCode(user.getEmail());
        verify(emailService, times(1)).SendPasswordResetCodeEmail(anyString(), anyString());
        challenge(response).setResendAfter(Instant.now().minusSeconds(1));
        passwordResetCodeService.resendCode(response.getChallengeId());
        assertNotEquals(oldCode, latestCode());
        assertTrue(passwordResetCodeService.verifyCode(response.getChallengeId(), oldCode).isEmpty());
        assertTrue(passwordResetCodeService.verifyCode(response.getChallengeId(), latestCode()).isPresent());
    }

    @Test
    void givesUnknownAddressesTheSameChallengeResponseButNeverAResetToken() {
        VerificationCodeResponse response = passwordResetCodeService.requestCode("unknown@example.test");
        assertNotNull(response.getChallengeId());
        assertNotNull(response.getExpiresAt());
        assertTrue(passwordResetCodeService.verifyCode(response.getChallengeId(), "000000").isEmpty());
        verifyNoInteractions(emailService);
        assertEquals(0, userTokenRepository.count());
    }

    @Test
    void publicEndpointsRequireValidCodeAndPasswordInput() throws Exception {
        VerificationCodeResponse response = passwordResetCodeService.requestCode(user.getEmail());
        String verificationCode = latestCode();
        mockMvc.perform(post("/api/auth/users/password-reset-code/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challengeId\":\"" + response.getChallengeId() + "\",\"code\":\"" + verificationCode + "\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.token").isString());
        assertTrue(passwordEncoder.matches("Original-Password1!", user.getPassword()));

        String resetToken = userTokenRepository.findAll().get(0).getToken();
        mockMvc.perform(post("/api/auth/users/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + resetToken + "\",\"newPassword\":\"weak\"}"))
                .andExpect(status().isBadRequest());
    }
}
