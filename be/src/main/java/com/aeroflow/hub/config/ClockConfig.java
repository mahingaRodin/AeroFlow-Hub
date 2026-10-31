package com.aeroflow.hub.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

    @Bean
    Clock systemUtcClock() {
        return Clock.systemUTC();
    }
}
