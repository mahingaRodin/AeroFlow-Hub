package com.aeroflow.hub.parser;

public record ParseIssue(int lineNumber, String code, String description, String rawContent) {
}
