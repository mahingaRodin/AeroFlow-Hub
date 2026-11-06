package com.aeroflow.hub.exception;

import com.aeroflow.hub.enums.ETravelClearanceStatus;

public class InvalidWorkflowTransitionException extends RuntimeException {

    public InvalidWorkflowTransitionException(ETravelClearanceStatus current, ETravelClearanceStatus target) {
        super("Cannot transition travel clearance from " + current + " to " + target);
    }
}
