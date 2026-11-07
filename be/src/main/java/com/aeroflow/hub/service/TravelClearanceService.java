package com.aeroflow.hub.service;

import com.aeroflow.hub.exception.ResourceNotFoundException;
import com.aeroflow.hub.model.TravelClearanceHistoryEntry;
import com.aeroflow.hub.model.TravelClearanceRequest;
import com.aeroflow.hub.enums.ETravelClearanceStatus;
import com.aeroflow.hub.repository.TravelClearanceHistoryRepository;
import com.aeroflow.hub.repository.TravelClearanceRequestRepository;
import com.aeroflow.hub.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TravelClearanceService {

    private final TravelClearanceRequestRepository requestRepository;
    private final TravelClearanceHistoryRepository historyRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    @Transactional
    public TravelClearanceRequest create(String username, String purpose, String destination, LocalDate travelDate) {
        var requester = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        var request = requestRepository.save(TravelClearanceRequest.create(purpose, destination, travelDate, requester));
        recordTransition(request, null, ETravelClearanceStatus.DRAFT, username);
        return request;
    }

    @Transactional(readOnly = true)
    public List<TravelClearanceRequest> findAllForRequester(String username) {
        return requestRepository.findAllByRequester_UsernameOrderByCreatedAtDesc(username);
    }

    @Transactional(readOnly = true)
    public Page<TravelClearanceRequest> findPendingForSupervisor(int page, int size) {
        return requestRepository.findAllByStatusOrderByCreatedAtAscIdAsc(
                ETravelClearanceStatus.PENDING_SUPERVISOR, PageRequest.of(page, size));
    }

    @Transactional(readOnly = true)
    public TravelClearanceRequest findForRequester(UUID id, String username) {
        return requestRepository.findByIdAndRequester_Username(id, username)
                .orElseThrow(() -> new ResourceNotFoundException("Travel clearance request not found"));
    }

    @Transactional(readOnly = true)
    public List<TravelClearanceHistoryEntry> findHistory(UUID id, String username, boolean reviewer) {
        if (reviewer) {
            requestRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Travel clearance request not found"));
        } else {
            findForRequester(id, username);
        }
        return historyRepository.findAllByRequest_IdOrderByChangedAtAscIdAsc(id);
    }

    @Transactional
    public TravelClearanceRequest submit(UUID id, String username) {
        var request = findForRequester(id, username);
        var previousStatus = request.getStatus();
        request.transitionTo(ETravelClearanceStatus.PENDING_SUPERVISOR);
        recordTransition(request, previousStatus, request.getStatus(), username);
        return request;
    }

    @Transactional
    public TravelClearanceRequest decide(UUID id, ETravelClearanceStatus decision, String reviewerUsername) {
        var request = requestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Travel clearance request not found"));
        var previousStatus = request.getStatus();
        request.transitionTo(decision);
        recordTransition(request, previousStatus, request.getStatus(), reviewerUsername);
        return request;
    }

    private void recordTransition(
            TravelClearanceRequest request,
            ETravelClearanceStatus previousStatus,
            ETravelClearanceStatus newStatus,
            String changedBy
    ) {
        historyRepository.save(TravelClearanceHistoryEntry.record(
                request, previousStatus, newStatus, changedBy, clock.instant()));
    }
}
