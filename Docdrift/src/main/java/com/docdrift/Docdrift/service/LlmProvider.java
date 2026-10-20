package com.docdrift.Docdrift.service;

import reactor.core.publisher.Mono;

/** An LLM that can run the drift prompt and return the suggestions as a JSON array string. */
public interface LlmProvider {

    /** Short id used in configuration, e.g. "gemini" or "claude". */
    String id();

    /** True when the provider has the credentials it needs. */
    boolean isConfigured();

    /** Runs the prompt; emits a JSON array of suggestions, or an error. */
    Mono<String> analyze(String prompt);
}
