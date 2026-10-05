package com.example.limitguard.model;

import com.example.limitguard.enums.UserRole;
import com.example.limitguard.enums.UserStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

// Represents an employee who uses the LimitGuard system
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "users")
public class User {
    // The unique ID for each user
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The user's first name
    @Column(nullable = false)
    private String firstName;

    // The user's last name
    @Column(nullable = false)
    private String lastName;

    // The user's email address, which will also be used for login
    @Column(nullable = false, unique = true)
    private String email;

    // The user's password
    @Column(nullable = false)
    private String password;

    // Shows if the user has verified their email
    @Column(nullable = false)
    private boolean emailVerified = false;

    // The user's role controls what they are allowed to do
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role = UserRole.RELATIONSHIP_MANAGER;

    // Shows if the user's account is active or deactivated
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status = UserStatus.ACTIVE;

    // Many users can belong to one financial institution
    @ManyToOne
    @JoinColumn(name = "financial_institution_id", nullable = false)
    private FinancialInstitution financialInstitution;

    // Stores the path of the user's profile picture
    @Column
    private String profilePicturePath;

    // Saves when the user was created
    @Column(nullable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    // Saves when the user was last updated
    @Column
    @UpdateTimestamp
    private LocalDateTime updatedAt;

}
