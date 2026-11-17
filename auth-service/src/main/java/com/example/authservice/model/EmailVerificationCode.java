package com.example.authservice.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;

// stores a verification challenge without storing the actual six-digit code
@Entity
@Table(name = "email_verification_codes")
@Getter
@Setter
@NoArgsConstructor
public class EmailVerificationCode {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // random identifier returned to the registration screen
    @Column(nullable = false, unique = true)
    private String challengeId;

    // a keyed hash, not the code sent by email
    @Column(nullable = false)
    private String codeDigest;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private Instant resendAfter;

    // limits incorrect guesses even if requests come from different IP addresses
    @Column(nullable = false)
    private int attempts;

    @Column(nullable = false)
    private boolean used;

    // each pending account has one current verification challenge
    @OneToOne
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;
}
