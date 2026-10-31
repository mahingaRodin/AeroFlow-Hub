package com.aeroflow.hub.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Configuration
public class CorsConfig {

    @Bean
    CorsFilter corsFilter(@Value("${aeroflow.cors.allowed-origins:*}") String allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        boolean isWildcard = "*".equals(allowedOrigins.trim());
        if (isWildcard) {
            config.addAllowedOriginPattern("*");
            config.setAllowCredentials(false);
        } else {
            for (String origin : allowedOrigins.split(",")) {
                config.addAllowedOrigin(origin.trim());
            }
            config.setAllowCredentials(true);
        }
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}