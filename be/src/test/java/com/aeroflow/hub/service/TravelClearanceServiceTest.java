package com.aeroflow.hub.service;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aeroflow.hub.model.TravelClearanceRequest;
import com.aeroflow.hub.enums.ETravelClearanceStatus;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class TravelClearanceServiceTest {

    @Mock
    private TravelClearanceRequestRepository requestRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private TravelClearanceService travelClearanceService;

    @Test
    void findsPagedRequestsAwaitingSupervisorReviewInRepositoryOrder() {
        var pendingRequests = List.of(
                TravelClearanceRequest.create(
                        "Crew positioning", "Nairobi", LocalDate.now(), new User()));
        var pageable = PageRequest.of(0, 20);
        var expectedPage = new PageImpl<>(pendingRequests, pageable, 41);
        when(requestRepository.findAllByStatusOrderByCreatedAtAscIdAsc(
                ETravelClearanceStatus.PENDING_SUPERVISOR, pageable))
                .thenReturn(expectedPage);

        var result = travelClearanceService.findPendingForSupervisor(0, 20);

        assertSame(expectedPage, result);
        verify(requestRepository).findAllByStatusOrderByCreatedAtAscIdAsc(
                ETravelClearanceStatus.PENDING_SUPERVISOR, pageable);
    }
}
