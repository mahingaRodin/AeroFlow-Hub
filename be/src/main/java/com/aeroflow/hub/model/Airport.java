package com.aeroflow.hub.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "airports", uniqueConstraints = @UniqueConstraint(columnNames = "iata_code"))
@Getter
@Setter
@NoArgsConstructor
public class Airport {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "iata_code", nullable = false, length = 3)
    private String iataCode;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;
}
