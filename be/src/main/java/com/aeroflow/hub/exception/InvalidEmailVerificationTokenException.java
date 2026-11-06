package com.aeroflow.hub.exception;

public class InvalidEmailVerificationTokenException extends RuntimeException {

    public InvalidEmailVerificationTokenException() {
        super("Email verification link is invalid or expired");
    }
}
