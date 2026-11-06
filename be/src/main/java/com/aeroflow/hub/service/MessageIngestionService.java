package com.aeroflow.hub.service;

import com.aeroflow.hub.enums.EMessageFormat;
import com.aeroflow.hub.model.QuarantineEntry;
import com.aeroflow.hub.model.QuarantineIssue;
import com.aeroflow.hub.exception.ResourceNotFoundException;
import com.aeroflow.hub.parser.ParseIssue;
import com.aeroflow.hub.parser.SitaTypeBMessageParser;
import com.aeroflow.hub.parser.SsimScheduleParser;
import com.aeroflow.hub.repository.QuarantineEntryRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MessageIngestionService {

    private final SitaTypeBMessageParser sitaParser;
    private final SsimScheduleParser ssimParser;
    private final QuarantineEntryRepository quarantineRepository;
    private final Duration retention;
    private final Clock clock;

    public MessageIngestionService(
            SitaTypeBMessageParser sitaParser,
            SsimScheduleParser ssimParser,
            QuarantineEntryRepository quarantineRepository,
            @Value("${app.ingestion.quarantine.retention-days:30}") int retentionDays,
            Clock clock
    ) {
        if (retentionDays < 1) {
            throw new IllegalArgumentException("Quarantine retention must be at least one day");
        }
        this.sitaParser = sitaParser;
        this.ssimParser = ssimParser;
        this.quarantineRepository = quarantineRepository;
        this.retention = Duration.ofDays(retentionDays);
        this.clock = clock;
    }

    @Transactional
    public IngestionResult ingestSita(String payload, String submittedBy) {
        var parsed = sitaParser.parseStream(payload);
        return completeIngestion(
                EMessageFormat.SITA_TYPE_B,
                payload,
                submittedBy,
                parsed.messages().size(),
                parsed.issueCount(),
                parsed.issuesTruncated(),
                parsed.issues());
    }

    @Transactional
    public IngestionResult ingestSsim(String payload, String submittedBy) {
        var parsed = ssimParser.parse(payload);
        return completeIngestion(
                EMessageFormat.SSIM,
                payload,
                submittedBy,
                parsed.records().size(),
                parsed.issueCount(),
                parsed.issuesTruncated(),
                parsed.issues());
    }

    private IngestionResult completeIngestion(
            EMessageFormat format,
            String payload,
            String submittedBy,
            int recordCount,
            int issueCount,
            boolean parserIssuesTruncated,
            List<ParseIssue> issues
    ) {
        if (issueCount == 0) {
            return new IngestionResult(format, recordCount, 0, null, false, false, List.of());
        }

        int storedIssueCount = Math.min(issueCount, QuarantineEntry.MAX_STORED_ISSUES);
        List<QuarantineIssue> storedIssues = issues.stream()
                .limit(QuarantineEntry.MAX_STORED_ISSUES)
                .map(QuarantineIssue::from)
                .toList();
        Instant now = clock.instant();
        QuarantineEntry entry = quarantineRepository.save(QuarantineEntry.create(
                format,
                payload,
                submittedBy,
                storedIssues,
                issueCount,
                parserIssuesTruncated || issueCount > storedIssueCount,
                now.plus(retention)));

        List<IssueSummary> summaries = issues.stream()
                .limit(QuarantineEntry.MAX_STORED_ISSUES)
                .map(issue -> new IssueSummary(issue.lineNumber(), issue.code(), issue.description()))
                .toList();
        return new IngestionResult(
                format,
                recordCount,
                issueCount,
                entry.getId(),
                true,
                parserIssuesTruncated || issueCount > storedIssueCount,
                summaries);
    }

    @Transactional(readOnly = true)
    public Page<QuarantineSummary> findQuarantine(Pageable pageable) {
        return quarantineRepository.findAllByExpiresAtAfter(clock.instant(), pageable)
                .map(QuarantineSummary::from);
    }

    @Transactional(readOnly = true)
    public QuarantineDetails findQuarantineEntry(UUID id) {
        QuarantineEntry entry = quarantineRepository.findByIdAndExpiresAtAfter(id, clock.instant())
                .orElseThrow(() -> new ResourceNotFoundException("Quarantine entry not found"));
        return QuarantineDetails.from(entry);
    }

    public record IssueSummary(int lineNumber, String code, String description) {
    }

    public record IngestionResult(
            EMessageFormat eMessageFormat,
            int recordCount,
            int issueCount,
            UUID quarantineId,
            boolean quarantined,
            boolean issuesTruncated,
            List<IssueSummary> issues
    ) {
    }

    public record QuarantineSummary(
            UUID id,
            EMessageFormat eMessageFormat,
            String submittedBy,
            int issueCount,
            boolean issuesTruncated,
            Instant createdAt,
            Instant expiresAt
    ) {
        static QuarantineSummary from(QuarantineEntry entry) {
            return new QuarantineSummary(
                    entry.getId(),
                    entry.getEMessageFormat(),
                    entry.getSubmittedBy(),
                    entry.getIssueCount(),
                    entry.isIssuesTruncated(),
                    entry.getCreatedAt(),
                    entry.getExpiresAt());
        }
    }

    public record QuarantineDetails(
            UUID id,
            EMessageFormat eMessageFormat,
            String submittedBy,
            String rawPayload,
            int issueCount,
            boolean issuesTruncated,
            Instant createdAt,
            Instant expiresAt,
            List<QuarantineIssueDetails> issues
    ) {
        static QuarantineDetails from(QuarantineEntry entry) {
            List<QuarantineIssueDetails> issueDetails = entry.getIssues().stream()
                    .map(issue -> new QuarantineIssueDetails(
                            issue.getLineNumber(),
                            issue.getCode(),
                            issue.getDescription(),
                            issue.getRawExcerpt()))
                    .toList();
            return new QuarantineDetails(
                    entry.getId(),
                    entry.getEMessageFormat(),
                    entry.getSubmittedBy(),
                    entry.getRawPayload(),
                    entry.getIssueCount(),
                    entry.isIssuesTruncated(),
                    entry.getCreatedAt(),
                    entry.getExpiresAt(),
                    issueDetails);
        }
    }

    public record QuarantineIssueDetails(
            int lineNumber,
            String code,
            String description,
            String rawExcerpt
    ) {
    }
}
