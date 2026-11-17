package com.example.authservice.service;

import com.example.authservice.dto.VerificationCodeResponse;
import com.example.authservice.exception.EmailAlreadyExistsException;
import com.example.authservice.exception.InvalidTokenException;
import com.example.authservice.model.EmailVerificationCode;
import com.example.authservice.model.User;
import com.example.authservice.repository.EmailVerificationCodeRepository;
import com.example.authservice.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Clock;
import java.util.UUID;

// creates and verifies short-lived email codes for pending registrations
@Service
public class EmailVerificationCodeService {

    private final EmailVerificationCodeRepository codes;
    private final UserRepository users;
    private final EmailService email;
    private final EmailCodeSupport emailCodeSupport;
    private final Clock clock;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Autowired
    private com.example.authservice.repository.UserTokenRepository legacyTokens;


    @Autowired
    public EmailVerificationCodeService(EmailVerificationCodeRepository codes, UserRepository users,
            EmailService email, @Value("${jwt-secret}") String secret) {
        this(codes, users, email, secret, Clock.systemUTC());
    }

    // lets tests check expiry and cooldown without waiting in real time
    EmailVerificationCodeService(EmailVerificationCodeRepository codes, UserRepository users,
            EmailService email, String secret, Clock clock) {
        this.codes = codes;
        this.users = users;
        this.email = email;
        this.emailCodeSupport = new EmailCodeSupport(secret);
        this.clock = clock;
    }

    @Transactional
    public VerificationCodeResponse create(User user) {
        EmailVerificationCode challenge = new EmailVerificationCode();
        challenge.setUser(user);
        challenge.setChallengeId(UUID.randomUUID().toString());
        return sendNewCode(challenge);
    }

    @Transactional
    public VerificationCodeResponse requestForPendingAccount(String address, String password) {
        // requires the correct password before reopening a pending registration
        User user = users.findByEmail(address)
                .orElseThrow(() -> new InvalidTokenException("Unable to resume email verification"));
        if (!passwordEncoder.matches(password, user.getPassword()) || user.isEmailVerified()
                || user.getStatus() != com.example.authservice.enums.UserStatus.ACTIVE) {
            throw new InvalidTokenException("Unable to resume email verification");
        }
        var existing = codes.findByUserId(user.getId());
        if (existing.isEmpty()) return create(user);
        EmailVerificationCode challenge = existing.get();
        if (clock.instant().isBefore(challenge.getResendAfter())) {
            return new VerificationCodeResponse(challenge.getChallengeId(), user.getEmail(),
                    challenge.getExpiresAt(), challenge.getResendAfter());
        }
        // rotates the identifier so previous browser recovery sessions cannot change this challenge
        challenge.setChallengeId(UUID.randomUUID().toString());
        return sendNewCode(challenge);
    }

    @Transactional
    public boolean verify(String challengeId, String code) {
        var found = codes.findByChallengeId(challengeId);
        if (found.isEmpty()) return false;
        EmailVerificationCode challenge = found.get();
        if (challenge.isUsed() || challenge.getUser().isEmailVerified()
                || !clock.instant().isBefore(challenge.getExpiresAt()) || challenge.getAttempts() >= 5) return false;

        // incorrect attempts are saved without throwing an exception that would roll them back
        challenge.setAttempts(challenge.getAttempts() + 1);
        boolean matches = emailCodeSupport.matches("email-code", challengeId, code, challenge.getCodeDigest());
        if (matches) {
            challenge.getUser().setEmailVerified(true);
            users.save(challenge.getUser());
            challenge.setUsed(true);
        }
        codes.save(challenge);
        return matches;
    }

    @Transactional
    public VerificationCodeResponse resend(String challengeId) {
        EmailVerificationCode challenge = pendingChallenge(challengeId);
        checkCooldown(challenge);
        // replacing the digest immediately invalidates the previous code
        return sendNewCode(challenge);
    }

    @Transactional
    public VerificationCodeResponse changeEmail(String challengeId, String address) {
        EmailVerificationCode challenge = pendingChallenge(challengeId);
        checkCooldown(challenge);
        // preserves old verification links without letting them verify a changed address
        if (legacyTokens.existsByUserIdAndTokenTypeAndUsedFalse(
                challenge.getUser().getId(), com.example.authservice.enums.UserTokenType.EMAIL_VERIFICATION)) {
            throw new InvalidTokenException("Contact your administrator to change the email on this existing link-based registration");
        }
        String newEmail = address.trim();
        User user = challenge.getUser();
        var existing = users.findByEmail(newEmail);
        if (existing.isPresent() && !existing.get().getId().equals(user.getId())) {
            throw new EmailAlreadyExistsException("An account with the same email is already registered");
        }
        // only changes this pending registration, never an already verified account
        user.setEmail(newEmail);
        users.save(user);
        challenge.setChallengeId(UUID.randomUUID().toString());
        return sendNewCode(challenge);
    }

    private EmailVerificationCode pendingChallenge(String challengeId) {
        EmailVerificationCode challenge = codes.findByChallengeId(challengeId)
                .orElseThrow(() -> new InvalidTokenException("Verification request is no longer available"));
        if (challenge.isUsed() || challenge.getUser().isEmailVerified()) {
            throw new InvalidTokenException("Verification request is no longer available");
        }
        return challenge;
    }

    private void checkCooldown(EmailVerificationCode challenge) {
        if (clock.instant().isBefore(challenge.getResendAfter())) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Please wait one minute before requesting another code");
        }
    }

    private VerificationCodeResponse sendNewCode(EmailVerificationCode challenge) {
        String code = emailCodeSupport.generateCode("email-code", challenge.getChallengeId(), challenge.getCodeDigest());
        challenge.setCodeDigest(emailCodeSupport.digest("email-code", challenge.getChallengeId(), code));
        challenge.setExpiresAt(clock.instant().plusSeconds(600));
        challenge.setResendAfter(clock.instant().plusSeconds(60));
        challenge.setAttempts(0);
        challenge.setUsed(false);
        codes.save(challenge);
        // mail failures roll back the challenge, keeping the previous code valid on failed resend
        email.SendVerificationCodeEmail(challenge.getUser().getEmail(), code);
        return new VerificationCodeResponse(challenge.getChallengeId(), challenge.getUser().getEmail(),
                challenge.getExpiresAt(), challenge.getResendAfter());
    }

}
