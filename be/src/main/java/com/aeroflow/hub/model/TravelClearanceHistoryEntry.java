package com.aeroflow.hub.model;

import com.aeroflow.hub.enums.ETravelClearanceStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "travel_clearance_history")
@Getter
@NoArgsConstructor
public class TravelClearanceHistoryEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    private TravelClearanceRequest request;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 32)
    private ETravelClearanceStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 32)
    private ETravelClearanceStatus newStatus;

    @Column(name = "changed_by", nullable = false, length = 100)
    private String changedBy;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    private TravelClearanceHistoryEntry(
            TravelClearanceRequest request,
            ETravelClearanceStatus previousStatus,
            ETravelClearanceStatus newStatus,
            String changedBy,
            Instant changedAt
    ) {
        this.request = request;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.changedBy = changedBy;
        this.changedAt = changedAt;
    }

    public static TravelClearanceHistoryEntry record(
            TravelClearanceRequest request,
            ETravelClearanceStatus previousStatus,
            ETravelClearanceStatus newStatus,
            String changedBy,
            Instant changedAt
    ) {
        return new TravelClearanceHistoryEntry(request, previousStatus, newStatus, changedBy, changedAt);
    }
}
