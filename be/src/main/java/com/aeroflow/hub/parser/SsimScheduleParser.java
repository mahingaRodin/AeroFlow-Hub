package com.aeroflow.hub.parser;

import java.util.ArrayList;
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
        List<SsimRecord> records = new ArrayList<>();
        List<ParseIssue> issues = new ArrayList<>();

        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            if (line.isBlank()) {
                continue;
            }

            Character recordType = null;
            if (line.charAt(0) >= '1' && line.charAt(0) <= '7') {
                recordType = line.charAt(0);
            } else {
                issues.add(new ParseIssue(
                        index + 1,
                        "UNRECOGNIZED_RECORD_TYPE",
                        "Expected an SSIM record type from 1 through 7",
                        line));
            }
            records.add(new SsimRecord(index + 1, recordType, line));
        }

        return new ParsedSsimSchedule(
                scheduleText, lines, List.copyOf(records), List.copyOf(issues));
    }

    public record ParsedSsimSchedule(
            String rawSchedule,
            List<String> lines,
            List<SsimRecord> records,
            List<ParseIssue> issues
    ) {
    }

    public record SsimRecord(int lineNumber, Character recordType, String rawRecord) {
    }
}
