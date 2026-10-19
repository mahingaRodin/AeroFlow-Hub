package com.aeroflow.hub.exception;

import com.aeroflow.hub.model.TravelClearanceStatus;

public class InvalidWorkflowTransitionException extends RuntimeException {

    public InvalidWorkflowTransitionException(TravelClearanceStatus current, TravelClearanceStatus target) {
        super("Cannot transition travel clearance from " + current + " to " + target);
    }
}
