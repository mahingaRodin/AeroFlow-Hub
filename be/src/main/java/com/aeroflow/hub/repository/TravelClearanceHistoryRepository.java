package com.aeroflow.hub.repository;

import com.aeroflow.hub.model.TravelClearanceHistoryEntry;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TravelClearanceHistoryRepository extends JpaRepository<TravelClearanceHistoryEntry, UUID> {

    List<TravelClearanceHistoryEntry> findAllByRequest_IdOrderByChangedAtAscIdAsc(UUID requestId);
}
