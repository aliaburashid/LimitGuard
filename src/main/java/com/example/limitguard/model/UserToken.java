package com.example.limitguard.model;

import com.example.limitguard.enums.UserTokenType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "user_tokens")
public class UserToken {

    // Primary key for the token
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The unique token that will be sent to the user
    @Column(nullable = false, unique = true)
    private String token;

    // Shows what the token is being used for
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserTokenType tokenType;

    // The time when the token can no longer be used
    @Column(nullable = false)
    private LocalDateTime expiresAt;

    // Shows if the token has already been used
    @Column(nullable = false)
    private boolean used = false;

    // The user that this token belongs to
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Automatically stores when the token was created
    @Column(nullable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;
}