package com.aeroflow.hub.controller;

import com.aeroflow.hub.service.FlightOperationsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/operations")
@Validated
public class FlightOperationsController {

    private final FlightOperationsService flightOperationsService;

    public FlightOperationsController(FlightOperationsService flightOperationsService) {
        this.flightOperationsService = flightOperationsService;
    }

    @PostMapping("/airports/import")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.OK)
    public ImportResult importAirports(@Valid @RequestBody AirportImportRequest request) {
        return new ImportResult(flightOperationsService.importAirports(
                request.airports().stream().map(AirportImportItem::toServiceInput).toList()));
    }

    @PostMapping("/flights/import")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.OK)
    public ImportResult importFlights(@Valid @RequestBody FlightImportRequest request) {
        return new ImportResult(flightOperationsService.importFlights(
                request.flights().stream().map(FlightImportItem::toServiceInput).toList()));
    }

    public record AirportImportRequest(
            @NotEmpty @Size(max = 5000) List<@NotNull @Valid AirportImportItem> airports
    ) {
    }

    public record AirportImportItem(
            @NotBlank @Size(max = 3) String iataCode,
            @NotBlank @Size(max = 150) String name,
            @NotBlank @Size(max = 2) String countryCode,
            double latitude,
            double longitude
    ) {
        FlightOperationsService.AirportImport toServiceInput() {
            return new FlightOperationsService.AirportImport(iataCode, name, countryCode, latitude, longitude);
        }
    }

    public record FlightImportRequest(
            @NotEmpty @Size(max = 10000) List<@NotNull @Valid FlightImportItem> flights
    ) {
    }

    public record FlightImportItem(
            @NotBlank @Size(max = 20) String flightNumber,
            @NotBlank @Size(max = 3) String departureAirport,
            @NotBlank @Size(max = 3) String arrivalAirport,
            @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant departureTime,
            @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant arrivalTime,
            @Size(max = 20) String aircraftRegistration
    ) {
        FlightOperationsService.FlightImport toServiceInput() {
            return new FlightOperationsService.FlightImport(
                    flightNumber,
                    departureAirport,
                    arrivalAirport,
                    departureTime,
                    arrivalTime,
                    aircraftRegistration);
        }
    }

    public record ImportResult(int importedOrUpdated) {
    }
}
