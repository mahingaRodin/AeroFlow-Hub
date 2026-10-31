package com.aeroflow.hub.parser;

import java.util.ArrayList;
import java.util.List;

final class ParseIssueCollector {

    static final int MAX_REPORTED_ISSUES = 100;

    private final List<ParseIssue> issues = new ArrayList<>();
    private int totalCount;

    void add(ParseIssue issue) {
        totalCount++;
        if (issues.size() < MAX_REPORTED_ISSUES) {
            issues.add(issue);
        }
    }

    List<ParseIssue> issues() {
        return List.copyOf(issues);
    }

    int totalCount() {
        return totalCount;
    }

    boolean truncated() {
        return totalCount > issues.size();
    }
}
