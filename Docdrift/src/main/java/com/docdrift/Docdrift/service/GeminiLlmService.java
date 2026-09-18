package com.docdrift.Docdrift.service;

import com.docdrift.Docdrift.dto.GeminiRequest;
import com.docdrift.Docdrift.dto.GeminiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
public class GeminiLlmService {

    private final WebClient webClient;
    private final String geminiApiKey;

    public GeminiLlmService(WebClient.Builder webClientBuilder,
                            @Value("${gemini.api.url}") String geminiApiUrl,
                            @Value("${gemini.api.key}") String geminiApiKey) {
        this.webClient = webClientBuilder.baseUrl(geminiApiUrl).build();
        this.geminiApiKey = geminiApiKey;
    }

    public Mono<String> analyzeDrift(String filePath, String diff, String docContent) {
        String prompt = "You are a Semantic Documentation Drift Detection Engine for software repositories.\n"
                + "Analyze the provided Git Code Diff against the target documentation file (" + filePath + ").\n\n"
                + "### Objectives:\n"
                + "1. Identify code symbols (REST endpoints, function signatures, CLI flags, configuration keys, database schemas, behavior changes) modified in the diff.\n"
                + "2. Cross-reference these symbols with the documentation content to detect semantic drift (outdated commands, invalid API signatures, removed config properties, broken sample code, or behavioral mismatches).\n"
                + "3. For each detected drift, generate a precise patch suggestion with semantic classification.\n\n"
                + "### Code Diff:\n" + diff + "\n\n"
                + "### Documentation File (" + filePath + "):\n" + docContent + "\n\n"
                + "### Response Rules:\n"
                + "- Respond ONLY with a valid JSON array matching this exact schema (no markdown explanations outside the JSON):\n"
                + "[\n"
                + "  {\n"
                + "    \"filePath\": \"" + filePath + "\",\n"
                + "    \"driftType\": \"API_SIGNATURE_MISMATCH\" | \"CONFIG_PROPERTY_CHANGED\" | \"CLI_USAGE_CHANGED\" | \"BEHAVIORAL_LOGIC_DRIFT\" | \"SAMPLE_CODE_BROKEN\",\n"
                + "    \"severity\": \"CRITICAL\" | \"HIGH\" | \"MEDIUM\" | \"LOW\",\n"
                + "    \"confidenceScore\": 0.95,\n"
                + "    \"impactedSymbol\": \"<e.g. POST /api/v2/users or spring.datasource.url>\",\n"
                + "    \"oldText\": \"<exact text in documentation that is outdated>\",\n"
                + "    \"suggestedText\": \"<exact updated text to replace oldText>\",\n"
                + "    \"reason\": \"<detailed explanation of what changed in the code and why this doc update is necessary>\"\n"
                + "  }\n"
                + "]\n"
                + "- If no semantic drift is detected, return an empty array: []";

        GeminiRequest request = GeminiRequest.builder()
                .contents(List.of(
                        GeminiRequest.Content.builder()
                                .parts(List.of(GeminiRequest.Part.builder().text(prompt).build()))
                                .build()
                ))
                .build();

        return this.webClient.post()
                .uri(uriBuilder -> uriBuilder.queryParam("key", geminiApiKey).build())
                .bodyValue(request)
                .retrieve()
                .bodyToMono(GeminiResponse.class)
                .map(response -> {
                    if (response.getCandidates() != null && !response.getCandidates().isEmpty()) {
                        String text = response.getCandidates().get(0).getContent().getParts().get(0).getText();
                        if (text != null) {
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
                    return "[]";
                })
                .onErrorResume(e -> {
                    System.err.println("Gemini API Error: " + e.getMessage());
                    return Mono.just("[]");
                });
    }
