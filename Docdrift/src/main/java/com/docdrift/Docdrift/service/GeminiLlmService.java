package com.docdrift.Docdrift.service;

import com.docdrift.Docdrift.dto.GeminiRequest;
import com.docdrift.Docdrift.dto.GeminiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class GeminiLlmService {

    private final WebClient webClient;
    private final String geminiModel;
    private final Duration retryBackoff;

    private static final Map<String, Object> STRING = Map.of("type", "STRING");
    private static final Map<String, Object> NULLABLE_STRING = Map.of("type", "STRING", "nullable", true);

    // Gemini's structured output mode guarantees the reply matches this schema
    private static final Map<String, Object> SUGGESTIONS_SCHEMA = Map.of(
            "type", "ARRAY",
            "items", Map.of(
                    "type", "OBJECT",
                    "properties", Map.ofEntries(
                            Map.entry("filePath", STRING),
                            Map.entry("driftType", Map.of("type", "STRING", "enum", List.of(
                                    "API_CONTRACT_BREAKING_CHANGE", "SCHEMA_DRIFT", "API_SIGNATURE_MISMATCH",
                                    "CONFIG_PROPERTY_CHANGED", "CLI_USAGE_CHANGED", "BEHAVIORAL_LOGIC_DRIFT",
                                    "SAMPLE_CODE_BROKEN"))),
                            Map.entry("severity", Map.of("type", "STRING", "enum", List.of("CRITICAL", "HIGH", "MEDIUM", "LOW"))),
                            Map.entry("confidenceScore", Map.of("type", "NUMBER")),
                            Map.entry("impactedSymbol", STRING),
                            Map.entry("isBreakingChange", Map.of("type", "BOOLEAN")),
                            Map.entry("httpMethod", NULLABLE_STRING),
                            Map.entry("endpointPath", NULLABLE_STRING),
                            Map.entry("schemaFormat", STRING),
                            Map.entry("oldText", STRING),
                            Map.entry("suggestedText", STRING),
                            Map.entry("reason", STRING)),
                    "required", List.of("filePath", "driftType", "severity", "confidenceScore", "isBreakingChange",
                            "oldText", "suggestedText", "reason")));

    public GeminiLlmService(WebClient.Builder webClientBuilder,
                            @Value("${gemini.api.url}") String geminiApiUrl,
                            @Value("${gemini.model}") String geminiModel,
                            @Value("${gemini.api.key}") String geminiApiKey,
                            @Value("${docdrift.http.retry-backoff:2s}") Duration retryBackoff) {
        this.retryBackoff = retryBackoff;
        // The key travels in a header rather than the URL so it does not end up in access logs
        this.webClient = webClientBuilder.clone()
                .baseUrl(geminiApiUrl)
                .defaultHeader("x-goog-api-key", geminiApiKey)
                .build();
        this.geminiModel = geminiModel;
    }

    public Mono<String> analyzeDrift(String filePath, String diff, String docContent) {
        String prompt = "You are an API Contract, Schema, and Documentation Drift Detection Engine for software repositories.\n"
                + "Analyze the provided Git Code Diff against the target documentation or API specification file (" + filePath + ").\n\n"
                + "### Objectives:\n"
                + "1. Identify code symbols (REST endpoints, HTTP methods, request/response DTOs, parameters, validation annotations, config keys, database schemas) modified in the diff.\n"
                + "2. Check if the diff modifies an API Contract (e.g. route path, HTTP method, required/optional fields, types, status codes, OpenAPI/Swagger YAML/JSON schemas, or Markdown API reference tables).\n"
                + "3. Determine whether any detected drift represents a BREAKING CHANGE for API consumers (e.g. renamed/deleted field, new required parameter, type alteration, route removal).\n"
                + "4. Generate precise patch suggestions with semantic and contract classification.\n\n"
                + "### Code Diff:\n" + diff + "\n\n"
                + "### Target File (" + filePath + "):\n" + docContent + "\n\n"
                + "### Response Rules:\n"
                + "- Respond ONLY with a valid JSON array matching this exact schema (no markdown explanations outside the JSON):\n"
                + "[\n"
                + "  {\n"
                + "    \"filePath\": \"" + filePath + "\",\n"
                + "    \"driftType\": \"API_CONTRACT_BREAKING_CHANGE\" | \"SCHEMA_DRIFT\" | \"API_SIGNATURE_MISMATCH\" | \"CONFIG_PROPERTY_CHANGED\" | \"CLI_USAGE_CHANGED\" | \"BEHAVIORAL_LOGIC_DRIFT\" | \"SAMPLE_CODE_BROKEN\",\n"
                + "    \"severity\": \"CRITICAL\" | \"HIGH\" | \"MEDIUM\" | \"LOW\",\n"
                + "    \"confidenceScore\": 0.95,\n"
                + "    \"impactedSymbol\": \"<e.g. POST /api/v1/orders or OrderRequest.userId>\",\n"
                + "    \"isBreakingChange\": true | false,\n"
                + "    \"httpMethod\": \"GET\" | \"POST\" | \"PUT\" | \"DELETE\" | \"PATCH\" | null,\n"
                + "    \"endpointPath\": \"/api/users\" | null,\n"
                + "    \"schemaFormat\": \"OPENAPI_YAML\" | \"SWAGGER_JSON\" | \"MARKDOWN_TABLE\" | \"GENERAL_PROSE\",\n"
                + "    \"oldText\": \"<exact text/schema snippet in documentation that is outdated>\",\n"
                + "    \"suggestedText\": \"<exact updated text/schema snippet to replace oldText>\",\n"
                + "    \"reason\": \"<detailed explanation of what changed in the API contract/code and why this doc/schema update is necessary>\"\n"
                + "  }\n"
                + "]\n"
                + "- oldText must be copied verbatim from the target file so it can be found and replaced exactly.\n"
                + "- If no semantic or contract drift is detected, return an empty array: []";

        GeminiRequest request = GeminiRequest.builder()
                .contents(List.of(
                        GeminiRequest.Content.builder()
                                .parts(List.of(GeminiRequest.Part.builder().text(prompt).build()))
                                .build()
                ))
                .generationConfig(GeminiRequest.GenerationConfig.builder()
                        .responseMimeType("application/json")
                        .responseSchema(SUGGESTIONS_SCHEMA)
                        .build())
                .build();

        return this.webClient.post()
                .uri("/models/{model}:generateContent", geminiModel)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(GeminiResponse.class)
                .retryWhen(HttpRetry.transientErrors(retryBackoff))
                .map(GeminiLlmService::extractJson);
    }

    /** Errors propagate to the caller so a failed analysis is recorded instead of looking like "no drift". */
    static String extractJson(GeminiResponse response) {
        if (response.getCandidates() == null || response.getCandidates().isEmpty()) {
            throw new IllegalStateException("Gemini returned no candidates");
        }
        GeminiResponse.Candidate candidate = response.getCandidates().get(0);
        if (candidate.getContent() == null || candidate.getContent().getParts() == null
                || candidate.getContent().getParts().isEmpty()) {
            throw new IllegalStateException("Gemini returned no content (finish reason: " + candidate.getFinishReason() + ")");
        }
        String text = candidate.getContent().getParts().get(0).getText();
        if (text == null || text.isBlank()) {
            return "[]";
        }
        // JSON mode should return bare JSON; strip code fences in case a model ignores it
        text = text.trim();
        if (text.startsWith("```json")) {
            text = text.substring(7);
        } else if (text.startsWith("```")) {
            text = text.substring(3);
        }
        if (text.endsWith("```")) {
            text = text.substring(0, text.length() - 3);
        }
        return text.trim();
    }
}

