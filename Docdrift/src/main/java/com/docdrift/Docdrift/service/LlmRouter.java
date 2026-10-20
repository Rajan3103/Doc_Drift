package com.docdrift.Docdrift.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Sends drift prompts to the configured LLM provider, and to a fallback provider if the primary one fails.
 * Set docdrift.llm.provider / docdrift.llm.fallback to "gemini" or "claude" (fallback may be "none").
 */
@Service
public class LlmRouter {

    private static final Logger log = LoggerFactory.getLogger(LlmRouter.class);

    private final Map<String, LlmProvider> providers;
    private final String primaryId;
    private final String fallbackId;

    public LlmRouter(List<LlmProvider> providers,
                     @Value("${docdrift.llm.provider:gemini}") String primaryId,
                     @Value("${docdrift.llm.fallback:none}") String fallbackId) {
        this.providers = providers.stream().collect(Collectors.toMap(LlmProvider::id, Function.identity()));
        this.primaryId = primaryId.trim().toLowerCase(Locale.ROOT);
        this.fallbackId = fallbackId.trim().toLowerCase(Locale.ROOT);
        if (!this.providers.containsKey(this.primaryId)) {
            throw new IllegalArgumentException("Unknown docdrift.llm.provider '" + primaryId
                    + "'; expected one of " + this.providers.keySet());
        }
        if (!"none".equals(this.fallbackId) && !this.providers.containsKey(this.fallbackId)) {
            throw new IllegalArgumentException("Unknown docdrift.llm.fallback '" + fallbackId
                    + "'; expected one of " + this.providers.keySet() + " or none");
        }
    }

    public Mono<String> analyzeDrift(String filePath, String diff, String docContent, List<String> contractFacts) {
        String prompt = DriftPrompt.build(filePath, diff, docContent, contractFacts);
        Mono<String> primary = providers.get(primaryId).analyze(prompt);

        LlmProvider fallback = providers.get(fallbackId);
        if (fallback == null || fallback.id().equals(primaryId) || !fallback.isConfigured()) {
            return primary;
        }
        return primary.onErrorResume(error -> {
            log.warn("{} failed for {} ({}); retrying with {}", primaryId, filePath, error.getMessage(), fallbackId);
            return fallback.analyze(prompt)
                    .onErrorMap(fallbackError -> new IllegalStateException(primaryId + " failed: " + error.getMessage()
                            + "; " + fallbackId + " failed: " + fallbackError.getMessage(), fallbackError));
        });
    }
}
