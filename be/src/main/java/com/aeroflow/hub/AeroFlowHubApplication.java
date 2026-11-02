package com.aeroflow.hub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class AeroFlowHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(AeroFlowHubApplication.class, args);
    }
}
