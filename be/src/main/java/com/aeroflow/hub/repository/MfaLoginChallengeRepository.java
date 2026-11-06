package com.aeroflow.hub.repository;

import com.aeroflow.hub.model.MfaLoginChallenge;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface MfaLoginChallengeRepository extends JpaRepository<MfaLoginChallenge, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<MfaLoginChallenge> findByTokenHash(String tokenHash);

    void deleteAllByUser_Id(UUID userId);
}
