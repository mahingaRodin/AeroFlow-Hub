package com.aeroflow.hub.controller;

import com.aeroflow.hub.model.TravelClearanceRequest;
import com.aeroflow.hub.model.TravelClearanceHistoryEntry;
import com.aeroflow.hub.enums.ETravelClearanceStatus;
import com.aeroflow.hub.service.TravelClearanceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/travel-clearance-requests")
@RequiredArgsConstructor
@Validated
public class TravelClearanceController {

    private final TravelClearanceService travelClearanceService;

    @PostMapping
    public TravelClearanceResponse create(
            Authentication authentication,
            @Valid @RequestBody CreateTravelClearanceRequest request
    ) {
        return toResponse(travelClearanceService.create(
                authentication.getName(), request.purpose(), request.destination(), request.travelDate()));
    }

    @GetMapping
    public List<TravelClearanceResponse> findMine(Authentication authentication) {
        return travelClearanceService.findAllForRequester(authentication.getName()).stream()
                .map(TravelClearanceController::toResponse)
                .toList();
    }

    @GetMapping("/pending")
    @PreAuthorize("hasRole('SUPERVISOR')")
    public PendingReviewPage findPendingForSupervisor(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        Page<TravelClearanceRequest> results = travelClearanceService.findPendingForSupervisor(page, size);
        return new PendingReviewPage(
                results.getContent().stream().map(TravelClearanceController::toResponse).toList(),
                results.getNumber(),
                results.getSize(),
                results.getTotalElements(),
                results.getTotalPages(),
                results.isLast());
    }

    @GetMapping("/{id}")
    public TravelClearanceResponse findMine(@PathVariable UUID id, Authentication authentication) {
        return toResponse(travelClearanceService.findForRequester(id, authentication.getName()));
    }

    @GetMapping("/{id}/history")
    public List<TravelClearanceHistoryResponse> findHistory(
            @PathVariable UUID id,
            Authentication authentication
    ) {
        boolean reviewer = authentication.getAuthorities().stream()
                .anyMatch(authority -> Set.of("ROLE_SUPERVISOR", "ROLE_ADMIN").contains(authority.getAuthority()));
        return travelClearanceService.findHistory(id, authentication.getName(), reviewer).stream()
                .map(TravelClearanceController::toHistoryResponse)
                .toList();
    }

    @PostMapping("/{id}/submit")
    public TravelClearanceResponse submit(@PathVariable UUID id, Authentication authentication) {
        return toResponse(travelClearanceService.submit(id, authentication.getName()));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('SUPERVISOR')")
    public TravelClearanceResponse approve(@PathVariable UUID id, Authentication authentication) {
        return toResponse(travelClearanceService.decide(
                id, ETravelClearanceStatus.APPROVED, authentication.getName()));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('SUPERVISOR')")
    public TravelClearanceResponse reject(@PathVariable UUID id, Authentication authentication) {
        return toResponse(travelClearanceService.decide(
                id, ETravelClearanceStatus.REJECTED, authentication.getName()));
    }

    private static TravelClearanceResponse toResponse(TravelClearanceRequest request) {
        return new TravelClearanceResponse(
                request.getId(),
                request.getPurpose(),
                request.getDestination(),
                request.getTravelDate(),
                request.getStatus(),
                request.getRequester().getUsername(),
                request.getCreatedAt(),
                request.getUpdatedAt());
    }

    private static TravelClearanceHistoryResponse toHistoryResponse(TravelClearanceHistoryEntry entry) {
        return new TravelClearanceHistoryResponse(
                entry.getPreviousStatus(),
                entry.getNewStatus(),
                entry.getChangedBy(),
                entry.getChangedAt());
    }

    public record TravelClearanceHistoryResponse(
            ETravelClearanceStatus previousStatus,
            ETravelClearanceStatus newStatus,
            String changedBy,
            Instant changedAt
    ) {
    }

    public record TravelClearanceResponse(
            UUID id,
            String purpose,
            String destination,
            LocalDate travelDate,
            ETravelClearanceStatus status,
            String requesterUsername,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record PendingReviewPage(
            List<TravelClearanceResponse> content,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean last
    ) {
    }

    public record CreateTravelClearanceRequest(
            @NotBlank @Size(max = 500) String purpose,
            @NotBlank @Size(max = 150) String destination,
            @NotNull @FutureOrPresent LocalDate travelDate
    ) {
    }
}
