package com.aeroflow.hub.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "scheduled_flights",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_scheduled_flights_number_departure",
                columnNames = {"flight_number", "departure_time"}),
        indexes = @Index(name = "idx_scheduled_flights_departure_time", columnList = "departure_time")
)
@Getter
@Setter
@NoArgsConstructor
public class ScheduledFlight {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "flight_number", nullable = false, length = 20)
    private String flightNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "departure_airport_id", nullable = false)
    private Airport departureAirport;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "arrival_airport_id", nullable = false)
    private Airport arrivalAirport;

    @Column(name = "departure_time", nullable = false)
    private Instant departureTime;

    @Column(name = "arrival_time", nullable = false)
    private Instant arrivalTime;

    @Column(name = "aircraft_registration", length = 20)
    private String aircraftRegistration;
}
