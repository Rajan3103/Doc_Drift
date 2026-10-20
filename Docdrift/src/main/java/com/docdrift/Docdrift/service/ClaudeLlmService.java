package com.docdrift.Docdrift.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.StructuredOutputConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

/** Runs the drift prompt on Claude through the Anthropic Java SDK, using structured outputs. */
@Service
public class ClaudeLlmService implements LlmProvider {

    /** Shape Claude must return; the SDK derives the JSON schema from these records. */
    public record ClaudeSuggestion(String filePath, String driftType, String severity, Double confidenceScore,
                                   String impactedSymbol, Boolean isBreakingChange, String httpMethod,
                                   String endpointPath, String schemaFormat, String oldText, String suggestedText,
                                   String reason) {
    }

    public record ClaudeDriftResult(List<ClaudeSuggestion> suggestions) {
    }

    private final AnthropicClient client; // null when no API key is configured
    private final String model;
    private final OutputConfig.Effort effort;
    private final ObjectMapper objectMapper;

    public ClaudeLlmService(@Value("${anthropic.api.key:}") String apiKey,
                            @Value("${anthropic.api.url:https://api.anthropic.com}") String apiUrl,
                            @Value("${anthropic.model:claude-opus-5-5}") String model,
                            @Value("${anthropic.effort:medium}") String effort,
                            ObjectMapper objectMapper) {
        this.client = apiKey == null || apiKey.isBlank() ? null : AnthropicOkHttpClient.builder()
                .apiKey(apiKey)
                .baseUrl(apiUrl)
                .maxRetries(2) // the SDK retries 429, 5xx and connection errors
                .timeout(Duration.ofMinutes(5))
                .build();
        this.model = model;
        this.effort = OutputConfig.Effort.of(effort.toLowerCase(Locale.ROOT));
        this.objectMapper = objectMapper;
    }

    @Override
    public String id() {
        return "claude";
    }

    @Override
    public boolean isConfigured() {
        return client != null;
    }

    @Override
    public Mono<String> analyze(String prompt) {
        if (client == null) {
            return Mono.error(new IllegalStateException("ANTHROPIC_API_KEY is not set"));
        }
        // The SDK call blocks, so run it off the reactive threads
        return Mono.fromCallable(() -> call(prompt)).subscribeOn(Schedulers.boundedElastic());
    }

    private String call(String prompt) {
        StructuredMessageCreateParams<ClaudeDriftResult> params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(16000L)
                .outputConfig(StructuredOutputConfig.<ClaudeDriftResult>builder()
                        .effort(effort)
                        .format(ClaudeDriftResult.class)
                        .build())
                // If a safety classifier declines the request, let the API retry it on a fallback model
                .putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
                .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
                .addUserMessage(prompt)
                .build();

        StructuredMessage<ClaudeDriftResult> message = client.messages().create(params);

        StopReason stopReason = message.rawMessage().stopReason().orElse(null);
        if (StopReason.REFUSAL.equals(stopReason)) {
            throw new IllegalStateException("Claude declined to analyse this document");
        }
        if (StopReason.MAX_TOKENS.equals(stopReason)) {
            throw new IllegalStateException("Claude's response was cut off at the token limit");
        }

        List<ClaudeSuggestion> suggestions = message.content().stream()
                .flatMap(block -> block.text().stream())
                .map(text -> text.text().suggestions())
                .findFirst()
                .orElse(List.of());
        return objectMapper.writeValueAsString(suggestions == null ? List.of() : suggestions);
    }
}
