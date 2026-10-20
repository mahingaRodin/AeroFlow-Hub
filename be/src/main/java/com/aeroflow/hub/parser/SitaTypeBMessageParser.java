package com.aeroflow.hub.parser;

import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class SitaTypeBMessageParser {

    public ParsedSitaMessage parse(String message) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("SITA message must not be blank");
        }
        List<String> lines = Arrays.stream(message.split("\\R"))
                .map(String::stripTrailing)
                .toList();
        return new ParsedSitaMessage(message, lines);
    }

    public record ParsedSitaMessage(String rawMessage, List<String> lines) {
    }
}
