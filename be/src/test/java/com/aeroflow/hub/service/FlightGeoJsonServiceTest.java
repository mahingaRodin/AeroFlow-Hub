package com.aeroflow.hub.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.aeroflow.hub.model.Airport;
import com.aeroflow.hub.model.ScheduledFlight;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FlightGeoJsonServiceTest {

    @Mock
    private FlightOperationsService flightOperationsService;

    @InjectMocks
    private FlightGeoJsonService flightGeoJsonService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void emitsGeoJsonFeatureCollectionWithLongitudeLatitudeOrderedFlightLine() throws Exception {
        Airport kigali = airport("KGL", -1.9686, 30.1395);
        Airport nairobi = airport("NBO", -1.3192, 36.9278);
        ScheduledFlight flight = new ScheduledFlight();
        flight.setId(UUID.randomUUID());
        flight.setFlightNumber("WB452");
        flight.setDepartureAirport(kigali);
        flight.setArrivalAirport(nairobi);
        flight.setDepartureTime(Instant.parse("2026-11-09T08:00:00Z"));
        flight.setArrivalTime(Instant.parse("2026-11-09T09:30:00Z"));
        when(flightOperationsService.findFlights(
                Instant.parse("2026-11-09T00:00:00Z"), Instant.parse("2026-11-10T00:00:00Z")))
                .thenReturn(List.of(flight));

        var collection = flightGeoJsonService.flights(
                Instant.parse("2026-11-09T00:00:00Z"), Instant.parse("2026-11-10T00:00:00Z"));

        assertEquals("FeatureCollection", collection.type());
        assertEquals(1, collection.features().size());
        var feature = collection.features().getFirst();
        assertEquals("LineString", ((FlightGeoJsonService.LineStringGeometry) feature.geometry()).type());
        assertEquals(
                List.of(List.of(30.1395, -1.9686), List.of(36.9278, -1.3192)),
                ((FlightGeoJsonService.LineStringGeometry) feature.geometry()).coordinates());
        assertEquals("WB452", feature.properties().get("flightNumber"));

        var json = objectMapper.readTree(objectMapper.writeValueAsString(collection));
        assertEquals("FeatureCollection", json.path("type").asText());
        assertEquals("LineString", json.path("features").get(0).path("geometry").path("type").asText());
        assertEquals(30.1395, json.path("features").get(0).path("geometry")
                .path("coordinates").get(0).get(0).asDouble());
        assertEquals(-1.9686, json.path("features").get(0).path("geometry")
                .path("coordinates").get(0).get(1).asDouble());
    }

    private static Airport airport(String code, double latitude, double longitude) {
        Airport airport = new Airport();
        airport.setId(UUID.randomUUID());
        airport.setIataCode(code);
        airport.setLatitude(latitude);
        airport.setLongitude(longitude);
        return airport;
    }
}
