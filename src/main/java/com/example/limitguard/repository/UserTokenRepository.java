package com.example.limitguard.repository;

import com.example.limitguard.model.UserToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

// Allows us to access and manage user tokens in the database
public interface UserTokenRepository extends JpaRepository<UserToken, Long> {

    // Finds a user token using the unique token value
    Optional<UserToken> findByToken(String token);
}