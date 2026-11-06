package com.aeroflow.hub.model;

import com.aeroflow.hub.enums.EMessageFormat;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "message_quarantine",
        indexes = {
                @Index(name = "idx_message_quarantine_created_at", columnList = "created_at"),
                @Index(name = "idx_message_quarantine_expires_at", columnList = "expires_at")
        }
)
@Getter
@NoArgsConstructor
public class QuarantineEntry extends AuditedEntity {

    public static final int MAX_STORED_ISSUES = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_format", nullable = false, length = 32)
    private EMessageFormat eMessageFormat;

    @Column(name = "raw_payload", nullable = false, columnDefinition = "text")
    private String rawPayload;

    @Column(name = "submitted_by", nullable = false, length = 100)
    private String submittedBy;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "message_quarantine_issues",
            joinColumns = @JoinColumn(name = "quarantine_id")
    )
    @OrderColumn(name = "issue_order")
    private List<QuarantineIssue> issues;

    @Column(name = "issue_count", nullable = false)
    private int issueCount;

    @Column(name = "issues_truncated", nullable = false)
    private boolean issuesTruncated;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    private QuarantineEntry(
            EMessageFormat eMessageFormat,
            String rawPayload,
            String submittedBy,
            List<QuarantineIssue> issues,
            int issueCount,
            boolean issuesTruncated,
            Instant expiresAt
    ) {
        this.eMessageFormat = eMessageFormat;
        this.rawPayload = rawPayload;
        this.submittedBy = submittedBy;
        this.issues = List.copyOf(issues);
        this.issueCount = issueCount;
        this.issuesTruncated = issuesTruncated;
        this.expiresAt = expiresAt;
    }

    public static QuarantineEntry create(
            EMessageFormat eMessageFormat,
            String rawPayload,
            String submittedBy,
            List<QuarantineIssue> issues,
            int issueCount,
            boolean issuesTruncated,
            Instant expiresAt
    ) {
        return new QuarantineEntry(
                eMessageFormat, rawPayload, submittedBy, issues, issueCount, issuesTruncated, expiresAt);
    }
}
