package com.example.limitguard.repository;

import com.example.limitguard.model.UserToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Optional;

// Allows us to access and manage user tokens in the database
public interface UserTokenRepository extends JpaRepository<UserToken, Long> {

    // checks for existing verification links without changing or invalidating them
    boolean existsByUserIdAndTokenTypeAndUsedFalse(Long userId, com.example.limitguard.enums.UserTokenType tokenType);

    // locks a reset token so concurrent requests cannot use it twice
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from UserToken token where token.token = :tokenValue")
    Optional<UserToken> findResetTokenForUpdate(@Param("tokenValue") String tokenValue);

    // Finds a user token using the unique token value
    Optional<UserToken> findByToken(String token);
}