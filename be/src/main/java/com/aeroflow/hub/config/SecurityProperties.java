package com.aeroflow.hub.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "aeroflow.security")

public class SecurityProperties {
    private long verificationTokenExpirationHours = 24;
    private long passwordResetTokenExpirationHours = 1;
    private long activationTokenExpirationHours = 72;
}
