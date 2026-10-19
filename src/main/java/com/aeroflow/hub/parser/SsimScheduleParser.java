package com.aeroflow.hub.parser;

import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class SsimScheduleParser {

    public ParsedSsimSchedule parse(String scheduleText) {
        if (scheduleText == null || scheduleText.isBlank()) {
            throw new IllegalArgumentException("SSIM schedule must not be blank");
        }
        List<String> lines = Arrays.stream(scheduleText.split("\\R"))
                .map(String::stripTrailing)
                .toList();
        return new ParsedSsimSchedule(scheduleText, lines);
    }

    public record ParsedSsimSchedule(String rawSchedule, List<String> lines) {
    }
}
