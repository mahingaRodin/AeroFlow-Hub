package com.aeroflow.hub.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aeroflow.hub.exception.InvalidWorkflowTransitionException;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class TravelClearanceRequestTest {

    @Test
    void allowsOnlyTheDefinedLifecycleTransitions() {
        var request = TravelClearanceRequest.create(
                "Crew positioning", "Nairobi", LocalDate.now(), new User());

        assertEquals(TravelClearanceStatus.DRAFT, request.getStatus());

        request.transitionTo(TravelClearanceStatus.PENDING_SUPERVISOR);
        assertEquals(TravelClearanceStatus.PENDING_SUPERVISOR, request.getStatus());

        request.transitionTo(TravelClearanceStatus.APPROVED);
        assertEquals(TravelClearanceStatus.APPROVED, request.getStatus());
        assertThrows(
                InvalidWorkflowTransitionException.class,
                () -> request.transitionTo(TravelClearanceStatus.REJECTED));
    }
}
