package com.aeroflow.hub.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "aeroflow.security.jwt")
public class JwtProperties {
    private String secret;
    private long accessTokenExpirationMinutes = 30;
    private long refreshTokenExpirationDays = 7;
}
