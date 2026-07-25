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

    public Mono<String> analyzeDrift(String diff, String docContent) {
        String prompt = "You are a Documentation Drift Detector. Review the following code diff and the documentation file content. "
                + "If the diff makes the documentation outdated, suggest a fix.\n\n"
                + "Code Diff:\n" + diff + "\n\n"
                + "Documentation:\n" + docContent + "\n\n"
                + "Respond ONLY with a JSON array in the following format (do not use markdown formatting like ```json):\n"
                + "[{ \"oldText\": \"...\", \"suggestedText\": \"...\", \"reason\": \"...\" }]\n"
                + "If no drift is found, return an empty array [].";

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
                        // Strip markdown blocks if Gemini added them
                        if(text.startsWith("```json")) {
                            text = text.replace("```json", "").replace("```", "").trim();
                        }
                        return text;
                    }
                    return "[]";
                })
                .onErrorResume(e -> Mono.just("[]"));
    }
}
