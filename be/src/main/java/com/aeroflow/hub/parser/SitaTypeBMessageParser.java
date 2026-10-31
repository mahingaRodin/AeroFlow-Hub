package com.aeroflow.hub.parser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class SitaTypeBMessageParser {

    public ParsedSitaMessage parse(String message) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("SITA message must not be blank");
        }
        List<String> lines = splitLines(message);
        return new ParsedSitaMessage(message, lines);
    }

    public ParsedSitaStream parseStream(String stream) {
        if (stream == null || stream.isBlank()) {
            throw new IllegalArgumentException("SITA message stream must not be blank");
        }

        List<ParsedSitaMessage> messages = new ArrayList<>();
        ParseIssueCollector issues = new ParseIssueCollector();
        List<String> currentLines = null;
        int startLine = 0;
        String[] lines = stream.split("\\R", -1);

        for (int index = 0; index < lines.length; index++) {
            String line = lines[index];
            String marker = line.strip();
            int lineNumber = index + 1;

            if (isStartMarker(marker)) {
                if (currentLines != null) {
                    issues.add(new ParseIssue(
                            startLine, "MISSING_END_MARKER", "Message was not closed by NNNN",
                            String.join("\n", currentLines)));
                }
                currentLines = new ArrayList<>();
                startLine = lineNumber;
                currentLines.add(line);
            } else if (isEndMarker(marker)) {
                if (currentLines == null) {
                    issues.add(new ParseIssue(
                            lineNumber, "UNEXPECTED_END_MARKER", "NNNN has no matching ZCZC",
                            line));
                } else {
                    currentLines.add(line);
                    String rawMessage = String.join("\n", currentLines);
                    messages.add(new ParsedSitaMessage(rawMessage, List.copyOf(currentLines)));
                    currentLines = null;
                }
            } else if (currentLines != null) {
                currentLines.add(line);
            } else if (!marker.isEmpty()) {
                issues.add(new ParseIssue(
                        lineNumber, "UNFRAMED_LINE", "Content is outside a ZCZC/NNNN message",
                        line));
            }
        }

        if (currentLines != null) {
            issues.add(new ParseIssue(
                    startLine, "MISSING_END_MARKER", "Message reached end of stream without NNNN",
                    String.join("\n", currentLines)));
        }

        return new ParsedSitaStream(
                List.copyOf(messages), issues.issues(), issues.totalCount(), issues.truncated());
    }

    private List<String> splitLines(String text) {
        return Arrays.stream(text.split("\\R"))
                .map(String::stripTrailing)
                .toList();
    }

    private boolean isStartMarker(String line) {
        return line.equals("ZCZC") || line.startsWith("ZCZC ");
    }

    private boolean isEndMarker(String line) {
        return line.equals("NNNN");
    }

    public record ParsedSitaMessage(String rawMessage, List<String> lines) {
    }

    public record ParsedSitaStream(
            List<ParsedSitaMessage> messages,
            List<ParseIssue> issues,
            int issueCount,
            boolean issuesTruncated
    ) {
    }
}
