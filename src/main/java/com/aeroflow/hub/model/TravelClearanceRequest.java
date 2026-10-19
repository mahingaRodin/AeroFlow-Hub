package com.aeroflow.hub.model;

import com.aeroflow.hub.exception.InvalidWorkflowTransitionException;
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
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "travel_clearance_requests")
@Getter
@NoArgsConstructor
public class TravelClearanceRequest extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 500)
    private String purpose;

    @Column(nullable = false, length = 150)
    private String destination;

    @Column(nullable = false)
    private LocalDate travelDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TravelClearanceStatus status = TravelClearanceStatus.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_id", nullable = false)
    private User requester;

    @Version
    private long version;

    private TravelClearanceRequest(String purpose, String destination, LocalDate travelDate, User requester) {
        this.purpose = purpose;
        this.destination = destination;
        this.travelDate = travelDate;
        this.requester = requester;
    }

    public static TravelClearanceRequest create(
            String purpose,
            String destination,
            LocalDate travelDate,
            User requester
    ) {
        return new TravelClearanceRequest(purpose, destination, travelDate, requester);
    }

    public void transitionTo(TravelClearanceStatus target) {
        boolean allowed = switch (status) {
            case DRAFT -> target == TravelClearanceStatus.PENDING_SUPERVISOR;
            case PENDING_SUPERVISOR ->
                    target == TravelClearanceStatus.APPROVED || target == TravelClearanceStatus.REJECTED;
            case APPROVED, REJECTED -> false;
        };

        if (!allowed) {
            throw new InvalidWorkflowTransitionException(status, target);
        }
        status = target;
    }
}
