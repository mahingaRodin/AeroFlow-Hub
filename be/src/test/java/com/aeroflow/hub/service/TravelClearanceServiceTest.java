package com.aeroflow.hub.service;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aeroflow.hub.model.TravelClearanceHistoryEntry;
import com.aeroflow.hub.model.TravelClearanceRequest;
import com.aeroflow.hub.enums.ETravelClearanceStatus;
import com.aeroflow.hub.model.User;
import com.aeroflow.hub.repository.TravelClearanceHistoryRepository;
import com.aeroflow.hub.repository.TravelClearanceRequestRepository;
import com.aeroflow.hub.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.mockito.ArgumentCaptor;
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
    private TravelClearanceHistoryRepository historyRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private Clock clock;

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

    @Test
    void recordsInitialDraftCreation() {
        User requester = new User();
        requester.setUsername("crew.member");
        var request = TravelClearanceRequest.create("Crew positioning", "Nairobi", LocalDate.now(), requester);
        when(userRepository.findByUsername("crew.member")).thenReturn(Optional.of(requester));
        when(requestRepository.save(any(TravelClearanceRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(clock.instant()).thenReturn(Instant.parse("2026-11-07T10:00:00Z"));

        travelClearanceService.create("crew.member", "Crew positioning", "Nairobi", LocalDate.now());

        ArgumentCaptor<TravelClearanceHistoryEntry> historyCaptor =
                ArgumentCaptor.forClass(TravelClearanceHistoryEntry.class);
        verify(historyRepository).save(historyCaptor.capture());
        assertNull(historyCaptor.getValue().getPreviousStatus());
        assertEquals(ETravelClearanceStatus.DRAFT, historyCaptor.getValue().getNewStatus());
        assertEquals("crew.member", historyCaptor.getValue().getChangedBy());
        assertEquals(Instant.parse("2026-11-07T10:00:00Z"), historyCaptor.getValue().getChangedAt());
    }

    @Test
    void recordsRequesterSubmissionTransition() {
        User requester = new User();
        requester.setUsername("crew.member");
        var request = TravelClearanceRequest.create("Crew positioning", "Nairobi", LocalDate.now(), requester);
        when(requestRepository.findByIdAndRequester_Username(request.getId(), "crew.member"))
                .thenReturn(Optional.of(request));
        when(clock.instant()).thenReturn(Instant.parse("2026-11-07T10:05:00Z"));

        travelClearanceService.submit(request.getId(), "crew.member");

        ArgumentCaptor<TravelClearanceHistoryEntry> historyCaptor =
                ArgumentCaptor.forClass(TravelClearanceHistoryEntry.class);
        verify(historyRepository).save(historyCaptor.capture());
        assertEquals(ETravelClearanceStatus.DRAFT, historyCaptor.getValue().getPreviousStatus());
        assertEquals(ETravelClearanceStatus.PENDING_SUPERVISOR, historyCaptor.getValue().getNewStatus());
        assertEquals("crew.member", historyCaptor.getValue().getChangedBy());
    }

    @Test
    void recordsSupervisorDecisionWithReviewerIdentity() {
        User requester = new User();
        requester.setUsername("crew.member");
        var request = TravelClearanceRequest.create("Crew positioning", "Nairobi", LocalDate.now(), requester);
        request.transitionTo(ETravelClearanceStatus.PENDING_SUPERVISOR);
        when(requestRepository.findById(request.getId())).thenReturn(Optional.of(request));
        when(clock.instant()).thenReturn(Instant.parse("2026-11-07T10:10:00Z"));

        travelClearanceService.decide(request.getId(), ETravelClearanceStatus.APPROVED, "supervisor.one");

        ArgumentCaptor<TravelClearanceHistoryEntry> historyCaptor =
                ArgumentCaptor.forClass(TravelClearanceHistoryEntry.class);
        verify(historyRepository).save(historyCaptor.capture());
        assertEquals(ETravelClearanceStatus.PENDING_SUPERVISOR, historyCaptor.getValue().getPreviousStatus());
        assertEquals(ETravelClearanceStatus.APPROVED, historyCaptor.getValue().getNewStatus());
        assertEquals("supervisor.one", historyCaptor.getValue().getChangedBy());
    }
}
