package com.docdrift.Docdrift.model;

import java.util.Locale;

public enum DriftType {
    API_CONTRACT_BREAKING_CHANGE,
    SCHEMA_DRIFT,
    API_SIGNATURE_MISMATCH,
    CONFIG_PROPERTY_CHANGED,
    CLI_USAGE_CHANGED,
    BEHAVIORAL_LOGIC_DRIFT,
    SAMPLE_CODE_BROKEN;

    /** Parses a value from LLM output; returns null if it is not a known drift type. */
    public static DriftType parse(String value) {
        if (value == null) return null;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
