CREATE TABLE airports (
    id UUID PRIMARY KEY,
    iata_code VARCHAR(3) NOT NULL,
    name VARCHAR(150) NOT NULL,
    country_code VARCHAR(2) NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    CONSTRAINT uk_airports_iata_code UNIQUE (iata_code)
);

CREATE TABLE scheduled_flights (
    id UUID PRIMARY KEY,
    flight_number VARCHAR(20) NOT NULL,
    departure_airport_id UUID NOT NULL,
    arrival_airport_id UUID NOT NULL,
    departure_time TIMESTAMP WITH TIME ZONE NOT NULL,
    arrival_time TIMESTAMP WITH TIME ZONE NOT NULL,
    aircraft_registration VARCHAR(20),
    CONSTRAINT uk_scheduled_flights_number_departure UNIQUE (flight_number, departure_time),
    CONSTRAINT fk_scheduled_flights_departure_airport
        FOREIGN KEY (departure_airport_id) REFERENCES airports (id),
    CONSTRAINT fk_scheduled_flights_arrival_airport
        FOREIGN KEY (arrival_airport_id) REFERENCES airports (id)
);

CREATE INDEX idx_scheduled_flights_departure_time ON scheduled_flights (departure_time);
