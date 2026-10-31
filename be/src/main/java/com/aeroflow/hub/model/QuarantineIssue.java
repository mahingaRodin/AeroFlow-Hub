package com.aeroflow.hub.model;

import com.aeroflow.hub.parser.ParseIssue;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor
public class QuarantineIssue {

    private static final int MAX_DESCRIPTION_LENGTH = 500;
    private static final int MAX_RAW_EXCERPT_LENGTH = 500;

    @Column(name = "line_number", nullable = false)
    private int lineNumber;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false, length = MAX_DESCRIPTION_LENGTH)
    private String description;

    @Column(name = "raw_excerpt", length = MAX_RAW_EXCERPT_LENGTH)
    private String rawExcerpt;

    private QuarantineIssue(ParseIssue issue) {
        this.lineNumber = issue.lineNumber();
        this.code = issue.code();
        this.description = truncate(issue.description(), MAX_DESCRIPTION_LENGTH);
        this.rawExcerpt = truncate(issue.rawContent(), MAX_RAW_EXCERPT_LENGTH);
    }

    public static QuarantineIssue from(ParseIssue issue) {
        return new QuarantineIssue(issue);
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
