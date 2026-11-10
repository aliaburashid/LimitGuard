package com.example.limitguard.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;

// shares code generation and hashing between registration and password recovery
@Component
public class EmailCodeSupport {

    private final SecureRandom secureRandom = new SecureRandom();
    private final String verificationSecret;

    public EmailCodeSupport(@Value("${jwt-secret}") String verificationSecret) {
        this.verificationSecret = verificationSecret;
    }

    // creates a six-digit string so leading zeros are kept
    public String generateCode(String purpose, String challengeId, String previousDigest) {
        String verificationCode;
        do {
            verificationCode = String.format(Locale.ROOT, "%06d", secureRandom.nextInt(1_000_000));
        } while (digest(purpose, challengeId, verificationCode).equals(previousDigest));
        return verificationCode;
    }

    // binds the code to its purpose so registration codes cannot reset passwords
    public String digest(String purpose, String challengeId, String verificationCode) {
        try {
            Mac messageAuthenticationCode = Mac.getInstance("HmacSHA256");
            messageAuthenticationCode.init(new SecretKeySpec(
                    verificationSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String protectedValue = purpose + ":" + challengeId + ":" + verificationCode;
            return HexFormat.of().formatHex(messageAuthenticationCode.doFinal(
                    protectedValue.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to protect verification code", exception);
        }
    }

    public boolean matches(String purpose, String challengeId, String verificationCode, String storedDigest) {
        return MessageDigest.isEqual(storedDigest.getBytes(StandardCharsets.UTF_8),
                digest(purpose, challengeId, verificationCode).getBytes(StandardCharsets.UTF_8));
    }
}
