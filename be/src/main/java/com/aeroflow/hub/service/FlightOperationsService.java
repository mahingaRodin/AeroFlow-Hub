package com.aeroflow.hub.service;

import com.aeroflow.hub.exception.ResourceNotFoundException;
import com.aeroflow.hub.model.Airport;
import com.aeroflow.hub.model.ScheduledFlight;
import com.aeroflow.hub.repository.AirportRepository;
import com.aeroflow.hub.repository.ScheduledFlightRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class FlightOperationsService {

    private static final Duration MAX_GIS_RANGE = Duration.ofDays(31);
    private static final int MAX_GIS_FEATURES = 10000;

    private final AirportRepository airportRepository;
    private final ScheduledFlightRepository scheduledFlightRepository;

    @Transactional
    public int importAirports(List<AirportImport> airports) {
        for (AirportImport input : airports) {
            String code = normalize(input.iataCode());
            String countryCode = normalize(input.countryCode());
            String name = input.name().strip();
            if (!code.matches("[A-Z]{3}")) {
                throw new IllegalArgumentException("Airport IATA code must contain exactly three letters");
            }
            if (!countryCode.matches("[A-Z]{2}")) {
                throw new IllegalArgumentException("Airport country code must contain exactly two letters");
            }
            if (!StringUtils.hasText(name) || name.length() > 150) {
                throw new IllegalArgumentException("Airport name must contain 1 to 150 characters");
            }
            if (!Double.isFinite(input.latitude()) || input.latitude() < -90 || input.latitude() > 90) {
                throw new IllegalArgumentException("Airport latitude must be between -90 and 90");
            }
            if (!Double.isFinite(input.longitude()) || input.longitude() < -180 || input.longitude() > 180) {
                throw new IllegalArgumentException("Airport longitude must be between -180 and 180");
            }

            Airport airport = airportRepository.findByIataCode(code).orElseGet(Airport::new);
            airport.setIataCode(code);
            airport.setCountryCode(countryCode);
            airport.setName(name);
            airport.setLatitude(input.latitude());
            airport.setLongitude(input.longitude());
            airportRepository.save(airport);
        }
        return airports.size();
    }

    @Transactional
    public int importFlights(List<FlightImport> flights) {
        for (FlightImport input : flights) {
            String flightNumber = normalize(input.flightNumber());
            if (!StringUtils.hasText(flightNumber) || flightNumber.length() > 20) {
                throw new IllegalArgumentException("Flight number must contain 1 to 20 characters");
            }
            if (input.departureTime() == null || input.arrivalTime() == null
                    || !input.arrivalTime().isAfter(input.departureTime())) {
                throw new IllegalArgumentException("Flight arrival time must be after its departure time");
            }

            Airport departure = airportRepository.findByIataCode(normalize(input.departureAirport()))
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Departure airport not found: " + normalize(input.departureAirport())));
            Airport arrival = airportRepository.findByIataCode(normalize(input.arrivalAirport()))
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Arrival airport not found: " + normalize(input.arrivalAirport())));
            if (departure.getId().equals(arrival.getId())) {
                throw new IllegalArgumentException("Departure and arrival airports must be different");
            }

            ScheduledFlight flight = scheduledFlightRepository
                    .findByFlightNumberAndDepartureTime(flightNumber, input.departureTime())
                    .orElseGet(ScheduledFlight::new);
            flight.setFlightNumber(flightNumber);
            flight.setDepartureAirport(departure);
            flight.setArrivalAirport(arrival);
            flight.setDepartureTime(input.departureTime());
            flight.setArrivalTime(input.arrivalTime());
            flight.setAircraftRegistration(normalizeOptional(input.aircraftRegistration()));
            scheduledFlightRepository.save(flight);
        }
        return flights.size();
    }

    @Transactional(readOnly = true)
    public List<Airport> findAirports() {
        return airportRepository.findAllByOrderByIataCodeAsc();
    }

    @Transactional(readOnly = true)
    public List<ScheduledFlight> findFlights(Instant from, Instant to) {
        if (from == null || to == null || !to.isAfter(from)) {
            throw new IllegalArgumentException("Flight query end must be after its start");
        }
        if (Duration.between(from, to).compareTo(MAX_GIS_RANGE) > 0) {
            throw new IllegalArgumentException("Flight query range must not exceed 31 days");
        }
        var page = scheduledFlightRepository.findAllByDepartureTimeGreaterThanEqualAndDepartureTimeLessThan(
                from, to, PageRequest.of(0, MAX_GIS_FEATURES, Sort.by("departureTime").ascending()));
        if (page.getTotalElements() > MAX_GIS_FEATURES) {
            throw new IllegalArgumentException(
                    "Flight query returns more than 10000 features; narrow the requested time range");
        }
        return page.getContent();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip().toUpperCase(Locale.ROOT);
    }

    private static String normalizeOptional(String value) {
        String normalized = normalize(value);
        return normalized.isEmpty() ? null : normalized;
    }

    public record AirportImport(
            String iataCode,
            String name,
            String countryCode,
            double latitude,
            double longitude
    ) {
    }

    public record FlightImport(
            String flightNumber,
            String departureAirport,
            String arrivalAirport,
            Instant departureTime,
            Instant arrivalTime,
            String aircraftRegistration
    ) {
    }
}
