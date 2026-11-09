package com.aeroflow.hub.repository;

import com.aeroflow.hub.model.ScheduledFlight;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduledFlightRepository extends JpaRepository<ScheduledFlight, UUID> {

    Optional<ScheduledFlight> findByFlightNumberAndDepartureTime(String flightNumber, Instant departureTime);

    @EntityGraph(attributePaths = {"departureAirport", "arrivalAirport"})
    Page<ScheduledFlight> findAllByDepartureTimeGreaterThanEqualAndDepartureTimeLessThan(
            Instant from, Instant to, Pageable pageable);
}
