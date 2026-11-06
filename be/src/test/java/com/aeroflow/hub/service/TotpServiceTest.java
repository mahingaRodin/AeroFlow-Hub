package com.aeroflow.hub.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TotpServiceTest {

    private final TotpService totpService = new TotpService();

    @Test
    void acceptsRfc6238SixDigitSha1TestVector() {
        String secret = TotpService.encodeBase32("12345678901234567890".getBytes(StandardCharsets.US_ASCII));

        assertTrue(totpService.matchingStep(secret, "287082", Instant.ofEpochSecond(59), -1).isPresent());
    }

    @Test
    void rejectsReplayOfPreviouslyAcceptedTimeStep() {
        String secret = TotpService.encodeBase32("12345678901234567890".getBytes(StandardCharsets.US_ASCII));

        assertTrue(totpService.matchingStep(secret, "287082", Instant.ofEpochSecond(59), 1).isEmpty());
    }

    @Test
    void rejectsMalformedCodes() {
        String secret = totpService.generateSecret();

        assertTrue(totpService.matchingStep(secret, "12x456", Instant.now(), -1).isEmpty());
        assertTrue(totpService.matchingStep(secret, "12345", Instant.now(), -1).isEmpty());
    }
}
