package com.aeroflow.hub.service;

import com.aeroflow.hub.model.Airport;
import com.aeroflow.hub.model.ScheduledFlight;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FlightGeoJsonService {

    private final FlightOperationsService flightOperationsService;

    public FeatureCollection airports() {
        List<Feature> features = flightOperationsService.findAirports().stream()
                .map(airport -> new Feature(
                        "Feature",
                        airport.getId().toString(),
                        new PointGeometry("Point", List.of(airport.getLongitude(), airport.getLatitude())),
                        airportProperties(airport)))
                .toList();
        return new FeatureCollection("FeatureCollection", features);
    }

    public FeatureCollection flights(Instant from, Instant to) {
        List<Feature> features = flightOperationsService.findFlights(from, to).stream()
                .map(FlightGeoJsonService::flightFeature)
                .toList();
        return new FeatureCollection("FeatureCollection", features);
    }

    private static Feature flightFeature(ScheduledFlight flight) {
        Airport departure = flight.getDepartureAirport();
        Airport arrival = flight.getArrivalAirport();
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("flightNumber", flight.getFlightNumber());
        properties.put("departureAirport", departure.getIataCode());
        properties.put("arrivalAirport", arrival.getIataCode());
        properties.put("departureTime", flight.getDepartureTime().toString());
        properties.put("arrivalTime", flight.getArrivalTime().toString());
        properties.put("aircraftRegistration", flight.getAircraftRegistration());
        return new Feature(
                "Feature",
                flight.getId().toString(),
                new LineStringGeometry(
                        "LineString",
                        List.of(
                                List.of(departure.getLongitude(), departure.getLatitude()),
                                List.of(arrival.getLongitude(), arrival.getLatitude()))),
                properties);
    }

    private static Map<String, Object> airportProperties(Airport airport) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("iataCode", airport.getIataCode());
        properties.put("name", airport.getName());
        properties.put("countryCode", airport.getCountryCode());
        return properties;
    }

    public record FeatureCollection(String type, List<Feature> features) {
    }

    public record Feature(String type, String id, Object geometry, Map<String, Object> properties) {
    }

    public record PointGeometry(String type, List<Double> coordinates) {
    }

    public record LineStringGeometry(String type, List<List<Double>> coordinates) {
    }
}
