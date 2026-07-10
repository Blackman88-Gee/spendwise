package com.spendwise.model;

public class Insight {

    public enum Severity {
        INFO, WARNING, CRITICAL
    }

    private final Severity severity;
    private final String message;

    public Insight(Severity severity, String message) {
        this.severity = severity;
        this.message = message;
    }

    public Severity getSeverity() {
        return severity;
    }

    public String getMessage() {
        return message;
    }
}
