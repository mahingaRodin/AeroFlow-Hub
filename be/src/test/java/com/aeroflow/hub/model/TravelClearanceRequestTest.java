package com.aeroflow.hub.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aeroflow.hub.enums.ETravelClearanceStatus;
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

        assertEquals(ETravelClearanceStatus.DRAFT, request.getStatus());

        request.transitionTo(ETravelClearanceStatus.PENDING_SUPERVISOR);
        assertEquals(ETravelClearanceStatus.PENDING_SUPERVISOR, request.getStatus());
        request.transitionTo(ETravelClearanceStatus.REJECTED);
        assertEquals(ETravelClearanceStatus.REJECTED, request.getStatus());

        request = TravelClearanceRequest.create(
                "Crew positioning", "Nairobi", LocalDate.now(), new User());
        request.transitionTo(ETravelClearanceStatus.PENDING_SUPERVISOR);
        request.transitionTo(ETravelClearanceStatus.APPROVED);
        assertEquals(ETravelClearanceStatus.APPROVED, request.getStatus());
    }

    @Test
    void rejectsEveryTransitionOutsideTheLifecycle() {
        Map<ETravelClearanceStatus, Set<ETravelClearanceStatus>> allowedTransitions =
                new EnumMap<>(ETravelClearanceStatus.class);
        allowedTransitions.put(
                ETravelClearanceStatus.DRAFT,
                EnumSet.of(ETravelClearanceStatus.PENDING_SUPERVISOR));
        allowedTransitions.put(
                ETravelClearanceStatus.PENDING_SUPERVISOR,
                EnumSet.of(ETravelClearanceStatus.APPROVED, ETravelClearanceStatus.REJECTED));
        allowedTransitions.put(
                ETravelClearanceStatus.APPROVED, EnumSet.noneOf(ETravelClearanceStatus.class));
        allowedTransitions.put(
                ETravelClearanceStatus.REJECTED, EnumSet.noneOf(ETravelClearanceStatus.class));

        for (ETravelClearanceStatus source : ETravelClearanceStatus.values()) {
            for (ETravelClearanceStatus target : ETravelClearanceStatus.values()) {
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

    private static TravelClearanceRequest requestInStatus(ETravelClearanceStatus status) {
        var request = TravelClearanceRequest.create(
                "Crew positioning", "Nairobi", LocalDate.now(), new User());
        if (status != ETravelClearanceStatus.DRAFT) {
            request.transitionTo(ETravelClearanceStatus.PENDING_SUPERVISOR);
        }
        if (status == ETravelClearanceStatus.APPROVED || status == ETravelClearanceStatus.REJECTED) {
            request.transitionTo(status);
        }
        return request;
    }
}
