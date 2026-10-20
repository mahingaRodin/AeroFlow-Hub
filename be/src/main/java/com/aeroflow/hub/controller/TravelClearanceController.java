package com.aeroflow.hub.controller;

import com.aeroflow.hub.model.TravelClearanceRequest;
import com.aeroflow.hub.model.TravelClearanceStatus;
import com.aeroflow.hub.service.TravelClearanceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/travel-clearance-requests")
@RequiredArgsConstructor
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

    @GetMapping("/{id}")
    public TravelClearanceResponse findMine(@PathVariable UUID id, Authentication authentication) {
        return toResponse(travelClearanceService.findForRequester(id, authentication.getName()));
    }

    @PostMapping("/{id}/submit")
    public TravelClearanceResponse submit(@PathVariable UUID id, Authentication authentication) {
        return toResponse(travelClearanceService.submit(id, authentication.getName()));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('SUPERVISOR')")
    public TravelClearanceResponse approve(@PathVariable UUID id) {
        return toResponse(travelClearanceService.decide(id, TravelClearanceStatus.APPROVED));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('SUPERVISOR')")
    public TravelClearanceResponse reject(@PathVariable UUID id) {
        return toResponse(travelClearanceService.decide(id, TravelClearanceStatus.REJECTED));
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

    public record TravelClearanceResponse(
            UUID id,
            String purpose,
            String destination,
            LocalDate travelDate,
            TravelClearanceStatus status,
            String requesterUsername,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record CreateTravelClearanceRequest(
            @NotBlank @Size(max = 500) String purpose,
            @NotBlank @Size(max = 150) String destination,
            @NotNull @FutureOrPresent LocalDate travelDate
    ) {
    }
}
