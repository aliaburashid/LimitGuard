package com.example.authservice;

import com.example.authservice.dto.RegisterRequest;
import com.example.authservice.dto.VerificationCodeResponse;
import com.example.authservice.enums.FinancialInstitutionStatus;
import com.example.authservice.enums.UserRole;
import com.example.authservice.model.EmailVerificationCode;
import com.example.authservice.model.FinancialInstitution;
import com.example.authservice.model.User;
import com.example.authservice.repository.*;
import com.example.authservice.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:auth_challenge_regression;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never",
        "jwt-secret=auth-regression-test-only-signing-key",
        "jwt-expiration-ms=60000",
        "internal-service-key=auth-regression-test-only-key"
})
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EmailVerificationCodeTest {
    @Autowired private org.springframework.test.web.servlet.MockMvc mvc;
    @Autowired private EmailVerificationCodeService verification;
    @Autowired private EmailVerificationCodeRepository codes;
    @Autowired private UserRepository users;
    @Autowired private FinancialInstitutionRepository institutions;
    @Autowired private PasswordEncoder passwords;
    @Autowired private UserService registration;
    @Autowired private UserTokenRepository legacyTokens;
    @MockitoBean private EmailService email;
    private User user;

    @BeforeEach
    void setUp() {
        FinancialInstitution institution = new FinancialInstitution();
        institution.setName("Verification Test Institution");
        institution.setStatus(FinancialInstitutionStatus.ACTIVE);
        institutions.saveAndFlush(institution);
        user = new User();
        user.setFirstName("Alex"); user.setLastName("Morgan");
        user.setEmail("pending@example.test");
        user.setPassword(passwords.encode("Fixture-Password1!"));
        user.setFinancialInstitution(institution);
        users.saveAndFlush(user);
    }

    private String latestCode() {
        ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
        verify(email, atLeastOnce()).SendVerificationCodeEmail(anyString(), sent.capture());
        return sent.getValue();
    }

    private EmailVerificationCode challenge(VerificationCodeResponse response) {
        return codes.findByChallengeId(response.getChallengeId()).orElseThrow();
    }

    @Test
    void registrationReturnsChallengeAndSendsCodeInsteadOfLink() {
        RegisterRequest request = new RegisterRequest();
        request.setFirstName("New"); request.setLastName("User");
        request.setEmail("new@example.test"); request.setPassword("Fixture-Password1!");
        request.setFinancialInstitutionId(user.getFinancialInstitution().getId());
        var response = registration.registerUser(request);
        assertNotNull(response.getVerification());
        assertFalse(response.isEmailVerified());
        assertTrue(latestCode().matches("[0-9]{6}"));
        verify(email, never()).SendVerificationEmail(anyString(), anyString());
    }

    @Test
    void storesOnlyDigestAndConsumesCodeOnceWithoutChangingRoleOrInstitution() {
        var response = verification.create(user);
        String code = latestCode();
        assertNotEquals(code, challenge(response).getCodeDigest());
        assertTrue(challenge(response).getCodeDigest().matches("[a-f0-9]{64}"));
        Long institutionId = user.getFinancialInstitution().getId();
        assertTrue(verification.verify(response.getChallengeId(), code));
        assertTrue(user.isEmailVerified());
        assertEquals(UserRole.RELATIONSHIP_MANAGER, user.getRole());
        assertEquals(institutionId, user.getFinancialInstitution().getId());
        assertFalse(verification.verify(response.getChallengeId(), code));
    }

    @Test
    void rejectsExpiredCodes() {
        var response = verification.create(user);
        challenge(response).setExpiresAt(Instant.now().minusSeconds(1));
        codes.flush();
        assertFalse(verification.verify(response.getChallengeId(), latestCode()));
        assertFalse(user.isEmailVerified());
    }

    @Test
    void savesFiveFailedAttemptsAndBlocksEvenTheCorrectCodeUntilResend() {
        var response = verification.create(user);
        String correct = latestCode();
        String incorrect = correct.equals("000000") ? "111111" : "000000";
        for (int count = 0; count < 5; count++) assertFalse(verification.verify(response.getChallengeId(), incorrect));
        codes.flush();
        assertEquals(5, challenge(response).getAttempts());
        assertFalse(verification.verify(response.getChallengeId(), correct));
        assertFalse(user.isEmailVerified());
    }

    @Test
    void enforcesResendCooldownAndInvalidatesTheOldCode() {
        var response = verification.create(user);
        String old = latestCode();
        assertThrows(ResponseStatusException.class, () -> verification.resend(response.getChallengeId()));
        challenge(response).setResendAfter(Instant.now().minusSeconds(1));
        verification.resend(response.getChallengeId());
        String replacement = latestCode();
        assertNotEquals(old, replacement);
        assertFalse(verification.verify(response.getChallengeId(), old));
        assertTrue(verification.verify(response.getChallengeId(), replacement));
    }

    @Test
    void changingPendingEmailRotatesChallengeAndRejectsOldCode() {
        var response = verification.create(user);
        String old = latestCode();
        challenge(response).setResendAfter(Instant.now().minusSeconds(1));
        var changed = verification.changeEmail(response.getChallengeId(), "corrected@example.test");
        codes.flush();
        assertEquals("corrected@example.test", user.getEmail());
        assertNotEquals(response.getChallengeId(), changed.getChallengeId());
        assertFalse(verification.verify(response.getChallengeId(), old));
        assertTrue(verification.verify(changed.getChallengeId(), latestCode()));
    }

    @Test
    void recoveryRequiresCorrectPasswordAndDoesNotSendDuringCooldown() {
        var response = verification.create(user);
        assertThrows(RuntimeException.class, () -> verification.requestForPendingAccount(user.getEmail(), "wrong-password"));
        var resumed = verification.requestForPendingAccount(user.getEmail(), "Fixture-Password1!");
        assertEquals(response.getChallengeId(), resumed.getChallengeId());
        verify(email, times(1)).SendVerificationCodeEmail(anyString(), anyString());
    }
    @Test
    void preservesExistingLinksAndPreventsThemFromVerifyingAChangedAddress() {
        var oldLink = new com.example.authservice.model.UserToken();
        oldLink.setUser(user);
        oldLink.setToken(java.util.UUID.randomUUID().toString());
        oldLink.setTokenType(com.example.authservice.enums.UserTokenType.EMAIL_VERIFICATION);
        oldLink.setExpiresAt(java.time.LocalDateTime.now().plusHours(24));
        legacyTokens.saveAndFlush(oldLink);
        var response = verification.create(user);
        challenge(response).setResendAfter(Instant.now().minusSeconds(1));
        assertThrows(com.example.authservice.exception.InvalidTokenException.class,
                () -> verification.changeEmail(response.getChallengeId(), "changed@example.test"));
        assertEquals("pending@example.test", user.getEmail());
        assertFalse(oldLink.isUsed());
        registration.verifyEmail(oldLink.getToken());
        assertTrue(user.isEmailVerified());
    }

    @Test
    void publicCodeEndpointAcceptsTheCodeAndRejectsItsReuse() throws Exception {
        var response = verification.create(user);
        String body = "{\"challengeId\":\"" + response.getChallengeId() + "\",\"code\":\"" + latestCode() + "\"}";
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/users/verification-code/verify")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(body))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control", "no-store"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/users/verification-code/verify")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(body))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
    }

}
