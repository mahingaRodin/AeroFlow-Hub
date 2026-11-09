package com.aeroflow.hub.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aeroflow.hub.model.Airport;
import com.aeroflow.hub.model.ScheduledFlight;
import com.aeroflow.hub.repository.AirportRepository;
import com.aeroflow.hub.repository.ScheduledFlightRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FlightOperationsServiceTest {

    @Mock
    private AirportRepository airportRepository;

    @Mock
    private ScheduledFlightRepository scheduledFlightRepository;

    @InjectMocks
    private FlightOperationsService flightOperationsService;

    @Test
    void importsAirportUsingNormalizedIataAndCountryCodes() {
        when(airportRepository.findByIataCode("KGL")).thenReturn(Optional.empty());

        flightOperationsService.importAirports(java.util.List.of(
                new FlightOperationsService.AirportImport(" kgl ", "Kigali International", " rw ", -1.9686, 30.1395)));

        ArgumentCaptor<Airport> captor = ArgumentCaptor.forClass(Airport.class);
        verify(airportRepository).save(captor.capture());
        assertEquals("KGL", captor.getValue().getIataCode());
        assertEquals("RW", captor.getValue().getCountryCode());
        assertEquals(-1.9686, captor.getValue().getLatitude());
    }

    @Test
    void importsScheduledFlightWithAirportLocationsAndTimes() {
        Airport kigali = airport("KGL");
        Airport nairobi = airport("NBO");
        when(airportRepository.findByIataCode("KGL")).thenReturn(Optional.of(kigali));
        when(airportRepository.findByIataCode("NBO")).thenReturn(Optional.of(nairobi));
        when(scheduledFlightRepository.findByFlightNumberAndDepartureTime(
                "WB452", Instant.parse("2026-11-09T08:00:00Z"))).thenReturn(Optional.empty());

        flightOperationsService.importFlights(java.util.List.of(new FlightOperationsService.FlightImport(
                " wb452 ",
                "kgl",
                "nbo",
                Instant.parse("2026-11-09T08:00:00Z"),
                Instant.parse("2026-11-09T09:30:00Z"),
                "9XR-WB")));

        ArgumentCaptor<ScheduledFlight> captor = ArgumentCaptor.forClass(ScheduledFlight.class);
        verify(scheduledFlightRepository).save(captor.capture());
        ScheduledFlight saved = captor.getValue();
        assertEquals("WB452", saved.getFlightNumber());
        assertEquals(kigali, saved.getDepartureAirport());
        assertEquals(nairobi, saved.getArrivalAirport());
        assertEquals("9XR-WB", saved.getAircraftRegistration());
    }

    @Test
    void rejectsFlightsWithArrivalBeforeDeparture() {
        var departure = Instant.parse("2026-11-09T10:00:00Z");
        var arrival = departure.minusSeconds(1);

        assertThrows(IllegalArgumentException.class, () -> flightOperationsService.importFlights(
                java.util.List.of(new FlightOperationsService.FlightImport(
                        "WB452", "KGL", "NBO", departure, arrival, null))));
    }

    private static Airport airport(String code) {
        Airport airport = new Airport();
        airport.setId(UUID.randomUUID());
        airport.setIataCode(code);
        return airport;
    }
}
