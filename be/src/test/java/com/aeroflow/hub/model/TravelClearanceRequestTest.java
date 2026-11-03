package com.aeroflow.hub.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aeroflow.hub.exception.InvalidWorkflowTransitionException;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TravelClearanceRequestTest {

    @Test
    void allowsSubmissionAndEitherSupervisorDecision() {
        var request = TravelClearanceRequest.create(
                "Crew positioning", "Nairobi", LocalDate.now(), new User());

        assertEquals(TravelClearanceStatus.DRAFT, request.getStatus());

        request.transitionTo(TravelClearanceStatus.PENDING_SUPERVISOR);
        assertEquals(TravelClearanceStatus.PENDING_SUPERVISOR, request.getStatus());
        request.transitionTo(TravelClearanceStatus.REJECTED);
        assertEquals(TravelClearanceStatus.REJECTED, request.getStatus());

        request = TravelClearanceRequest.create(
                "Crew positioning", "Nairobi", LocalDate.now(), new User());
        request.transitionTo(TravelClearanceStatus.PENDING_SUPERVISOR);
        request.transitionTo(TravelClearanceStatus.APPROVED);
        assertEquals(TravelClearanceStatus.APPROVED, request.getStatus());
    }

    @Test
    void rejectsEveryTransitionOutsideTheLifecycle() {
        Map<TravelClearanceStatus, Set<TravelClearanceStatus>> allowedTransitions =
                new EnumMap<>(TravelClearanceStatus.class);
        allowedTransitions.put(
                TravelClearanceStatus.DRAFT,
                EnumSet.of(TravelClearanceStatus.PENDING_SUPERVISOR));
        allowedTransitions.put(
                TravelClearanceStatus.PENDING_SUPERVISOR,
                EnumSet.of(TravelClearanceStatus.APPROVED, TravelClearanceStatus.REJECTED));
        allowedTransitions.put(
                TravelClearanceStatus.APPROVED, EnumSet.noneOf(TravelClearanceStatus.class));
        allowedTransitions.put(
                TravelClearanceStatus.REJECTED, EnumSet.noneOf(TravelClearanceStatus.class));

        for (TravelClearanceStatus source : TravelClearanceStatus.values()) {
            for (TravelClearanceStatus target : TravelClearanceStatus.values()) {
                if (allowedTransitions.get(source).contains(target)) {
                    continue;
                }

                var request = requestInStatus(source);
                assertThrows(
                        InvalidWorkflowTransitionException.class,
                        () -> request.transitionTo(target),
                        () -> "Expected transition " + source + " -> " + target + " to be rejected");
                assertEquals(source, request.getStatus());
            }
        }
    }

    private static TravelClearanceRequest requestInStatus(TravelClearanceStatus status) {
        var request = TravelClearanceRequest.create(
                "Crew positioning", "Nairobi", LocalDate.now(), new User());
        if (status != TravelClearanceStatus.DRAFT) {
            request.transitionTo(TravelClearanceStatus.PENDING_SUPERVISOR);
        }
        if (status == TravelClearanceStatus.APPROVED || status == TravelClearanceStatus.REJECTED) {
            request.transitionTo(status);
        }
        return request;
    }
}
