package com.docdrift.Docdrift.model;

/** Where a suggestion came from: the LLM, or DocDrift's deterministic API contract check. */
public enum SuggestionSource {
    AI, RULE
}
