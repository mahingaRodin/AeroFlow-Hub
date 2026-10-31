package com.aeroflow.hub.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class SitaTypeBMessageParserTest {

    private final SitaTypeBMessageParser parser = new SitaTypeBMessageParser();

    @Test
    void parsesMultipleFramedMessagesAndPreservesTheirLines() {
        var result = parser.parseStream("""
                ZCZC AB123
                QX message body
                NNNN
                ZCZC CD456
                second body
                NNNN
                """);

        assertEquals(2, result.messages().size());
        assertEquals(List.of("ZCZC AB123", "QX message body", "NNNN"),
                result.messages().get(0).lines());
        assertEquals(List.of("ZCZC CD456", "second body", "NNNN"),
                result.messages().get(1).lines());
        assertEquals(0, result.issues().size());
    }

    @Test
    void reportsUnframedAndUnterminatedContentWithoutReturningItAsAValidMessage() {
        var result = parser.parseStream("""
                stray line
                ZCZC AB123
                incomplete body
                """);

        assertEquals(0, result.messages().size());
        assertEquals(List.of("UNFRAMED_LINE", "MISSING_END_MARKER"),
                result.issues().stream().map(ParseIssue::code).toList());
        assertEquals(1, result.issues().get(0).lineNumber());
    }

    @Test
    void rejectsBlankInput() {
        assertThrows(IllegalArgumentException.class, () -> parser.parseStream(" \r\n"));
    }
}
