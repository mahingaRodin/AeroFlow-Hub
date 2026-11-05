package com.aeroflow.hub.service;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aeroflow.hub.model.TravelClearanceRequest;
import com.aeroflow.hub.model.TravelClearanceStatus;
import com.aeroflow.hub.model.User;
import com.aeroflow.hub.repository.TravelClearanceRequestRepository;
import com.aeroflow.hub.repository.UserRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TravelClearanceServiceTest {

    @Mock
    private TravelClearanceRequestRepository requestRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private TravelClearanceService travelClearanceService;

    @Test
    void findsRequestsAwaitingSupervisorReviewInRepositoryOrder() {
        List<TravelClearanceRequest> pendingRequests = List.of(
                TravelClearanceRequest.create(
                        "Crew positioning", "Nairobi", LocalDate.now(), new User()));
        when(requestRepository.findAllByStatusOrderByCreatedAtAsc(TravelClearanceStatus.PENDING_SUPERVISOR))
                .thenReturn(pendingRequests);

        List<TravelClearanceRequest> result = travelClearanceService.findPendingForSupervisor();

        assertSame(pendingRequests, result);
        verify(requestRepository).findAllByStatusOrderByCreatedAtAsc(TravelClearanceStatus.PENDING_SUPERVISOR);
    }
}
