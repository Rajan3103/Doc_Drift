package com.docdrift.Docdrift.service;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmRouterTest {

    /** Test provider that records prompts and returns a fixed result or error. */
    private static class FakeProvider implements LlmProvider {
        final String id;
        final boolean configured;
        final Mono<String> result;
        final List<String> prompts = new ArrayList<>();

        FakeProvider(String id, boolean configured, Mono<String> result) {
            this.id = id;
            this.configured = configured;
            this.result = result;
        }

        public String id() { return id; }
        public boolean isConfigured() { return configured; }
        public Mono<String> analyze(String prompt) {
            prompts.add(prompt);
            return result;
        }
    }

    @Test
    void usesPrimaryAndIncludesContractFactsInPrompt() {
        FakeProvider gemini = new FakeProvider("gemini", true, Mono.just("[1]"));
        FakeProvider claude = new FakeProvider("claude", true, Mono.just("[2]"));
        LlmRouter router = new LlmRouter(List.of(gemini, claude), "gemini", "claude");

        assertThat(router.analyzeDrift("openapi.yaml", "diff", "doc", List.of("GET /x was removed")).block()).isEqualTo("[1]");
        assertThat(claude.prompts).isEmpty();
        assertThat(gemini.prompts.getFirst()).contains("Verified API Contract Differences").contains("- GET /x was removed");
    }

    @Test
    void fallsBackWhenPrimaryFails() {
        FakeProvider gemini = new FakeProvider("gemini", true, Mono.error(new IllegalStateException("quota")));
        FakeProvider claude = new FakeProvider("claude", true, Mono.just("[2]"));
        LlmRouter router = new LlmRouter(List.of(gemini, claude), "gemini", "claude");

        assertThat(router.analyzeDrift("README.md", "diff", "doc", List.of()).block()).isEqualTo("[2]");
    }

    @Test
    void reportsBothErrorsWhenFallbackAlsoFails() {
        FakeProvider gemini = new FakeProvider("gemini", true, Mono.error(new IllegalStateException("quota")));
        FakeProvider claude = new FakeProvider("claude", true, Mono.error(new IllegalStateException("overloaded")));
        LlmRouter router = new LlmRouter(List.of(gemini, claude), "gemini", "claude");

        assertThatThrownBy(() -> router.analyzeDrift("README.md", "diff", "doc", List.of()).block())
                .hasMessageContaining("gemini failed: quota").hasMessageContaining("claude failed: overloaded");
    }

    @Test
    void skipsUnconfiguredFallbackAndRejectsUnknownProviders() {
        FakeProvider gemini = new FakeProvider("gemini", true, Mono.error(new IllegalStateException("quota")));
        FakeProvider claude = new FakeProvider("claude", false, Mono.just("[2]"));
        LlmRouter router = new LlmRouter(List.of(gemini, claude), "gemini", "claude");
        assertThatThrownBy(() -> router.analyzeDrift("README.md", "diff", "doc", List.of()).block()).hasMessage("quota");

        assertThatThrownBy(() -> new LlmRouter(List.of(gemini, claude), "openai", "none"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
