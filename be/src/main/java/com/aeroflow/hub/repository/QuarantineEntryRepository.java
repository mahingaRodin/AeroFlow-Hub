package com.aeroflow.hub.repository;

import com.aeroflow.hub.model.QuarantineEntry;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuarantineEntryRepository extends JpaRepository<QuarantineEntry, UUID> {

    Page<QuarantineEntry> findAllByExpiresAtAfter(Instant now, Pageable pageable);

    @EntityGraph(attributePaths = "issues")
    Optional<QuarantineEntry> findByIdAndExpiresAtAfter(UUID id, Instant now);

    int deleteAllByExpiresAtBefore(Instant now);
}
