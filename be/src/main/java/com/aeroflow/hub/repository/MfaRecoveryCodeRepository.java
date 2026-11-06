package com.aeroflow.hub.repository;

import com.aeroflow.hub.model.MfaRecoveryCode;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface MfaRecoveryCodeRepository extends JpaRepository<MfaRecoveryCode, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<MfaRecoveryCode> findByUser_IdAndCodeHash(UUID userId, String codeHash);

    void deleteAllByUser_Id(UUID userId);
}
