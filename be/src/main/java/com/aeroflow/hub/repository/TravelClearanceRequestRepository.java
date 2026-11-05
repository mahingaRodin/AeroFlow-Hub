package com.aeroflow.hub.repository;

import com.aeroflow.hub.model.TravelClearanceRequest;
import com.aeroflow.hub.model.TravelClearanceStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TravelClearanceRequestRepository extends JpaRepository<TravelClearanceRequest, UUID> {

    @EntityGraph(attributePaths = "requester")
    List<TravelClearanceRequest> findAllByStatusOrderByCreatedAtAsc(TravelClearanceStatus status);

    @EntityGraph(attributePaths = "requester")
    List<TravelClearanceRequest> findAllByRequester_UsernameOrderByCreatedAtDesc(String username);

    @EntityGraph(attributePaths = "requester")
    Optional<TravelClearanceRequest> findByIdAndRequester_Username(UUID id, String username);

    @Override
    @EntityGraph(attributePaths = "requester")
    Optional<TravelClearanceRequest> findById(UUID id);
}
