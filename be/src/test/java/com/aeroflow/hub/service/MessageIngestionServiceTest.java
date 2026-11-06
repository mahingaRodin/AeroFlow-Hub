package com.aeroflow.hub.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aeroflow.hub.model.QuarantineEntry;
import com.aeroflow.hub.enums.EMessageFormat;
import com.aeroflow.hub.parser.SitaTypeBMessageParser;
import com.aeroflow.hub.parser.SsimScheduleParser;
import com.aeroflow.hub.repository.QuarantineEntryRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MessageIngestionServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-31T09:00:00Z");

    @Mock
    private QuarantineEntryRepository quarantineRepository;

    private MessageIngestionService ingestionService;

    @BeforeEach
    void setUp() {
        ingestionService = new MessageIngestionService(
                new SitaTypeBMessageParser(),
                new SsimScheduleParser(),
                quarantineRepository,
                30,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void validSitaStreamIsParsedWithoutCreatingQuarantineEntry() {
        var result = ingestionService.ingestSita(
                "ZCZC AB123\nmessage body\nNNNN", "crew.user");

        assertEquals(EMessageFormat.SITA_TYPE_B, result.eMessageFormat());
        assertEquals(1, result.recordCount());
        assertFalse(result.quarantined());
        assertEquals(0, result.issueCount());
        verifyNoInteractions(quarantineRepository);
    }

    @Test
    void malformedSsimIsStoredWithExpiryAndCappedIssueDetails() {
        when(quarantineRepository.save(any(QuarantineEntry.class))).thenAnswer(invocation -> {
            QuarantineEntry entry = invocation.getArgument(0);
            ReflectionTestUtils.setField(entry, "id", UUID.randomUUID());
            return entry;
        });

        var result = ingestionService.ingestSsim("1HEADER\nXINVALID", "ops.user");

        ArgumentCaptor<QuarantineEntry> captor = ArgumentCaptor.forClass(QuarantineEntry.class);
        verify(quarantineRepository).save(captor.capture());
        QuarantineEntry stored = captor.getValue();

        assertTrue(result.quarantined());
        assertFalse(result.issuesTruncated());
        assertEquals(1, result.issueCount());
        assertNotNull(result.quarantineId());
        assertEquals(EMessageFormat.SSIM, stored.getEMessageFormat());
        assertEquals("ops.user", stored.getSubmittedBy());
        assertEquals("1HEADER\nXINVALID", stored.getRawPayload());
        assertEquals(NOW.plusSeconds(30 * 24 * 60 * 60L), stored.getExpiresAt());
        assertEquals(1, stored.getIssues().size());
        assertEquals("XINVALID", stored.getIssues().get(0).getRawExcerpt());
    }

    @Test
    void capsStoredIssueDetailsWhenInputContainsManyInvalidRecords() {
        when(quarantineRepository.save(any(QuarantineEntry.class))).thenAnswer(invocation -> {
            QuarantineEntry entry = invocation.getArgument(0);
            ReflectionTestUtils.setField(entry, "id", UUID.randomUUID());
            return entry;
        });
        String payload = "X\n".repeat(QuarantineEntry.MAX_STORED_ISSUES + 1);

        var result = ingestionService.ingestSsim(payload, "ops.user");

        ArgumentCaptor<QuarantineEntry> captor = ArgumentCaptor.forClass(QuarantineEntry.class);
        verify(quarantineRepository).save(captor.capture());
        assertTrue(result.issuesTruncated());
        assertEquals(QuarantineEntry.MAX_STORED_ISSUES + 1, result.issueCount());
        assertEquals(QuarantineEntry.MAX_STORED_ISSUES, captor.getValue().getIssues().size());
        assertTrue(captor.getValue().isIssuesTruncated());
    }

    @Test
    void rejectsNonPositiveRetentionPeriod() {
        assertThrows(IllegalArgumentException.class, () -> new MessageIngestionService(
                new SitaTypeBMessageParser(),
                new SsimScheduleParser(),
                quarantineRepository,
                0,
                Clock.fixed(NOW, ZoneOffset.UTC)));
    }
}
