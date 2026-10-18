package com.pbl4.server.repository;

import com.pbl4.server.model.VerificationCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VerificationCodeRepository
        extends JpaRepository<VerificationCode, Long> {

    Optional<VerificationCode>
    findTopByAccountAccountIdOrderByCreatedAtDesc(Long accountId);
}