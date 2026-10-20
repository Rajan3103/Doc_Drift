package com.docdrift.Docdrift.model;

import java.util.Locale;

public enum Severity {
    CRITICAL, HIGH, MEDIUM, LOW;

    /** Parses a value from LLM output or a query parameter; returns null if it is not a known severity. */
    public static Severity parse(String value) {
        if (value == null) return null;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
