package com.example.authservice.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;

// stores the current password-recovery code and its attempt limits
@Entity
@Table(name = "password_reset_codes")
@Getter
@Setter
@NoArgsConstructor
public class PasswordResetCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String challengeId;

    // also records requests for unknown addresses so responses follow the same flow
    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String codeDigest;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private Instant resendAfter;

    @Column(nullable = false)
    private int attempts;

    @Column(nullable = false)
    private boolean used;

    // an unknown address has no account attached and can never produce a reset token
    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;
}
