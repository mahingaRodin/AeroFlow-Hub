package com.aeroflow.hub.service;

import com.aeroflow.hub.exception.ResourceNotFoundException;
import com.aeroflow.hub.model.TravelClearanceRequest;
import com.aeroflow.hub.model.TravelClearanceStatus;
import com.aeroflow.hub.repository.TravelClearanceRequestRepository;
import com.aeroflow.hub.repository.UserRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TravelClearanceService {

    private final TravelClearanceRequestRepository requestRepository;
    private final UserRepository userRepository;

    @Transactional
    public TravelClearanceRequest create(String username, String purpose, String destination, LocalDate travelDate) {
        var requester = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return requestRepository.save(TravelClearanceRequest.create(purpose, destination, travelDate, requester));
    }

    @Transactional(readOnly = true)
    public List<TravelClearanceRequest> findAllForRequester(String username) {
        return requestRepository.findAllByRequester_UsernameOrderByCreatedAtDesc(username);
    }

    @Transactional(readOnly = true)
    public TravelClearanceRequest findForRequester(UUID id, String username) {
        return requestRepository.findByIdAndRequester_Username(id, username)
                .orElseThrow(() -> new ResourceNotFoundException("Travel clearance request not found"));
    }

    @Transactional
    public TravelClearanceRequest submit(UUID id, String username) {
        var request = findForRequester(id, username);
        request.transitionTo(TravelClearanceStatus.PENDING_SUPERVISOR);
        return request;
    }

    @Transactional
    public TravelClearanceRequest decide(UUID id, TravelClearanceStatus decision) {
        var request = requestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Travel clearance request not found"));
        request.transitionTo(decision);
        return request;
    }
}
