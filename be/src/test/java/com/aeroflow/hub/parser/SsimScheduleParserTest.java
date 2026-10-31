package com.aeroflow.hub.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SsimScheduleParserTest {

    private final SsimScheduleParser parser = new SsimScheduleParser();

    @Test
    void identifiesRecordTypesAndPreservesRawRecordContent() {
        var result = parser.parse("""
                1HEADER
                2FLIGHT
                3CONTINUATION
                4ADDITIONAL
                5SUMMARY
                6TRAILER
                7END
                """);

        assertEquals(7, result.records().size());
        assertEquals('1', result.records().get(0).recordType());
        assertEquals("2FLIGHT", result.records().get(1).rawRecord());
        assertEquals(0, result.issues().size());
    }

    @Test
    void reportsUnknownRecordTypesAndPreservesTheLineForQuarantine() {
        var result = parser.parse("1HEADER\nXUNKNOWN\n");

        assertEquals(2, result.records().size());
        assertNull(result.records().get(1).recordType());
        assertEquals("UNRECOGNIZED_RECORD_TYPE", result.issues().get(0).code());
        assertEquals(2, result.issues().get(0).lineNumber());
        assertEquals("XUNKNOWN", result.issues().get(0).rawContent());
    }

    @Test
    void rejectsBlankInput() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse("\r\n "));
    }

    @Test
    void capsReportedIssuesButPreservesTheTotalIssueCount() {
        String schedule = "X\n".repeat(ParseIssueCollector.MAX_REPORTED_ISSUES + 1);

        var result = parser.parse(schedule);

        assertEquals(ParseIssueCollector.MAX_REPORTED_ISSUES, result.issues().size());
        assertEquals(ParseIssueCollector.MAX_REPORTED_ISSUES + 1, result.issueCount());
        assertTrue(result.issuesTruncated());
    }
}
