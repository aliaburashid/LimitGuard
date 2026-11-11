package com.example.authservice.service;

import com.example.authservice.dto.VerificationCodeResponse;
import com.example.authservice.enums.UserStatus;
import com.example.authservice.enums.UserTokenType;
import com.example.authservice.exception.InvalidTokenException;
import com.example.authservice.model.PasswordResetCode;
import com.example.authservice.model.UserToken;
import com.example.authservice.repository.PasswordResetCodeRepository;
import com.example.authservice.repository.UserRepository;
import com.example.authservice.repository.UserTokenRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

// issues a short-lived reset token only after the emailed code is verified
@Service
public class PasswordResetCodeService {

    private final PasswordResetCodeRepository passwordResetCodeRepository;
    private final UserRepository userRepository;
    private final UserTokenRepository userTokenRepository;
    private final EmailCodeSupport emailCodeSupport;
    private final EmailService emailService;
    private final Clock clock;

    @Autowired
    public PasswordResetCodeService(PasswordResetCodeRepository passwordResetCodeRepository,
            UserRepository userRepository, UserTokenRepository userTokenRepository,
            EmailCodeSupport emailCodeSupport, EmailService emailService) {
        this(passwordResetCodeRepository, userRepository, userTokenRepository,
                emailCodeSupport, emailService, Clock.systemUTC());
    }

    // lets tests check expiry without waiting in real time
    PasswordResetCodeService(PasswordResetCodeRepository passwordResetCodeRepository,
            UserRepository userRepository, UserTokenRepository userTokenRepository,
            EmailCodeSupport emailCodeSupport, EmailService emailService, Clock clock) {
        this.passwordResetCodeRepository = passwordResetCodeRepository;
        this.userRepository = userRepository;
        this.userTokenRepository = userTokenRepository;
        this.emailCodeSupport = emailCodeSupport;
        this.emailService = emailService;
        this.clock = clock;
    }

    @Transactional
    public VerificationCodeResponse requestCode(String emailAddress) {
        String email = emailAddress.trim();
        PasswordResetCode passwordResetCode = passwordResetCodeRepository.findByEmail(email)
                .orElseGet(() -> {
                    PasswordResetCode newPasswordResetCode = new PasswordResetCode();
                    newPasswordResetCode.setEmail(email);
                    newPasswordResetCode.setChallengeId(UUID.randomUUID().toString());
                    return newPasswordResetCode;
                });

        if (passwordResetCode.getResendAfter() != null
                && clock.instant().isBefore(passwordResetCode.getResendAfter())) {
            return convertToResponse(passwordResetCode);
        }

        // unknown and deactivated accounts receive the same response but no email
        passwordResetCode.setUser(userRepository.findByEmail(email)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE).orElse(null));
        return sendNewCode(passwordResetCode);
    }

    @Transactional
    public VerificationCodeResponse resendCode(String challengeId) {
        PasswordResetCode passwordResetCode = passwordResetCodeRepository.findByChallengeId(challengeId)
                .orElseThrow(() -> new InvalidTokenException("Request a new password reset code"));

        if (clock.instant().isBefore(passwordResetCode.getResendAfter())) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Please wait one minute before requesting another code");
        }
        return sendNewCode(passwordResetCode);
    }

    @Transactional
    public Optional<String> verifyCode(String challengeId, String verificationCode) {
        Optional<PasswordResetCode> savedPasswordResetCode = passwordResetCodeRepository.findByChallengeId(challengeId);
        if (savedPasswordResetCode.isEmpty()) return Optional.empty();

        PasswordResetCode passwordResetCode = savedPasswordResetCode.get();
        if (passwordResetCode.isUsed() || passwordResetCode.getAttempts() >= 5
                || !clock.instant().isBefore(passwordResetCode.getExpiresAt())) return Optional.empty();

        // failed attempts commit normally so another request cannot reset the counter
        passwordResetCode.setAttempts(passwordResetCode.getAttempts() + 1);
        boolean correctCode = emailCodeSupport.matches("password-reset-code", challengeId,
                verificationCode, passwordResetCode.getCodeDigest());
        if (!correctCode || passwordResetCode.getUser() == null
                || passwordResetCode.getUser().getStatus() != UserStatus.ACTIVE
                || !passwordResetCode.getEmail().equals(passwordResetCode.getUser().getEmail())) {
            passwordResetCodeRepository.save(passwordResetCode);
            return Optional.empty();
        }

        passwordResetCode.setUsed(true);
        passwordResetCodeRepository.save(passwordResetCode);

        // uses the existing password-reset token and password validation flow
        UserToken passwordResetToken = new UserToken();
        passwordResetToken.setUser(passwordResetCode.getUser());
        passwordResetToken.setToken(UUID.randomUUID().toString());
        passwordResetToken.setTokenType(UserTokenType.PASSWORD_RESET);
        passwordResetToken.setExpiresAt(LocalDateTime.ofInstant(clock.instant(), ZoneId.systemDefault()).plusMinutes(5));
        userTokenRepository.save(passwordResetToken);
        return Optional.of(passwordResetToken.getToken());
    }

    private VerificationCodeResponse sendNewCode(PasswordResetCode passwordResetCode) {
        String verificationCode = emailCodeSupport.generateCode("password-reset-code",
                passwordResetCode.getChallengeId(), passwordResetCode.getCodeDigest());
        passwordResetCode.setCodeDigest(emailCodeSupport.digest("password-reset-code",
                passwordResetCode.getChallengeId(), verificationCode));
        passwordResetCode.setExpiresAt(clock.instant().plusSeconds(600));
        passwordResetCode.setResendAfter(clock.instant().plusSeconds(60));
        passwordResetCode.setAttempts(0);
        passwordResetCode.setUsed(false);
        passwordResetCodeRepository.saveAndFlush(passwordResetCode);

        if (passwordResetCode.getUser() != null
                && passwordResetCode.getUser().getStatus() == UserStatus.ACTIVE
                && passwordResetCode.getEmail().equals(passwordResetCode.getUser().getEmail())) {
            // email failures roll back the replacement code, leaving the previous code valid
            emailService.SendPasswordResetCodeEmail(passwordResetCode.getEmail(), verificationCode);
        }
        return convertToResponse(passwordResetCode);
    }

    private VerificationCodeResponse convertToResponse(PasswordResetCode passwordResetCode) {
        return new VerificationCodeResponse(passwordResetCode.getChallengeId(), passwordResetCode.getEmail(),
                passwordResetCode.getExpiresAt(), passwordResetCode.getResendAfter());
    }
}
