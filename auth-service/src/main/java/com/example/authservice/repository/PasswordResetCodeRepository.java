package com.example.authservice.repository;

import com.example.authservice.model.PasswordResetCode;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import java.util.Optional;

public interface PasswordResetCodeRepository extends JpaRepository<PasswordResetCode, Long> {

    // serializes requests and resends for the same email address
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PasswordResetCode> findByEmail(String email);

    // prevents concurrent attempts from consuming one code more than once
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PasswordResetCode> findByChallengeId(String challengeId);
}
