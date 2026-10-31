package com.aeroflow.hub.service;

import com.aeroflow.hub.repository.QuarantineEntryRepository;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class QuarantineRetentionJob {

    private final QuarantineEntryRepository quarantineRepository;
    private final Clock clock;

    @Scheduled(cron = "${app.ingestion.quarantine.cleanup-cron:0 15 3 * * *}", zone = "UTC")
    @Transactional
    public void deleteExpiredEntries() {
        int deleted = quarantineRepository.deleteAllByExpiresAtBefore(clock.instant());
        if (deleted > 0) {
            log.info("Deleted {} expired message quarantine entries", deleted);
        }
    }
}
