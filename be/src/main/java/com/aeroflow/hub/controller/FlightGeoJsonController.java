package com.aeroflow.hub.controller;

import com.aeroflow.hub.service.FlightGeoJsonService;
import java.time.Instant;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/gis")
@PreAuthorize("hasAnyRole('SUPERVISOR', 'ADMIN')")
public class FlightGeoJsonController {

    private final FlightGeoJsonService flightGeoJsonService;

    public FlightGeoJsonController(FlightGeoJsonService flightGeoJsonService) {
        this.flightGeoJsonService = flightGeoJsonService;
    }

    @GetMapping(value = "/airports", produces = "application/geo+json")
    public FlightGeoJsonService.FeatureCollection airports() {
        return flightGeoJsonService.airports();
    }

    @GetMapping(value = "/flights", produces = "application/geo+json")
    public FlightGeoJsonService.FeatureCollection flights(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        return flightGeoJsonService.flights(from, to);
    }
}
