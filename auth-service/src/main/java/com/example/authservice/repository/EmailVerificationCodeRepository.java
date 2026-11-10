package com.example.authservice.repository;

import com.example.authservice.model.EmailVerificationCode;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import java.util.Optional;

public interface EmailVerificationCodeRepository extends JpaRepository<EmailVerificationCode, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<EmailVerificationCode> findByUserId(Long userId);

    // locks the challenge so two requests cannot reuse a code or bypass attempt limits
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<EmailVerificationCode> findByChallengeId(String challengeId);
}
