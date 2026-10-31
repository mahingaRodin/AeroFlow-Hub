package com.aeroflow.hub.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aeroflow.hub.repository.QuarantineEntryRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QuarantineRetentionJobTest {

    @Mock
    private QuarantineEntryRepository quarantineRepository;

    @Test
    void deletesEntriesExpiredAtTheCurrentInstant() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-31T09:00:00Z"), ZoneOffset.UTC);
        when(quarantineRepository.deleteAllByExpiresAtBefore(clock.instant())).thenReturn(2);
        var job = new QuarantineRetentionJob(quarantineRepository, clock);

        job.deleteExpiredEntries();

        verify(quarantineRepository).deleteAllByExpiresAtBefore(clock.instant());
    }
}
