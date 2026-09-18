package com.docdrift.Docdrift.service;

import com.docdrift.Docdrift.dto.LlmSuggestionDto;
import com.docdrift.Docdrift.dto.WebhookPayload;
import com.docdrift.Docdrift.model.DriftReport;
import com.docdrift.Docdrift.model.DriftSuggestion;
import com.docdrift.Docdrift.model.WebhookEvent;
import com.docdrift.Docdrift.repository.DriftReportRepository;
import com.docdrift.Docdrift.repository.WebhookEventRepository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class DiffAnalyzerService {

    private final GitHubService gitHubService;
    private final GeminiLlmService geminiLlmService;
    private final DriftReportRepository driftReportRepository;
    private final WebhookEventRepository webhookEventRepository;
    private final ObjectMapper objectMapper;

    public DiffAnalyzerService(GitHubService gitHubService,
                               GeminiLlmService geminiLlmService,
                               DriftReportRepository driftReportRepository,
                               WebhookEventRepository webhookEventRepository,
                               ObjectMapper objectMapper) {
        this.gitHubService = gitHubService;
        this.geminiLlmService = geminiLlmService;
        this.driftReportRepository = driftReportRepository;
        this.webhookEventRepository = webhookEventRepository;
        this.objectMapper = objectMapper;
    }

    private static final List<String> DOC_CANDIDATES = List.of(
            "README.md",
            "openapi.yaml",
            "openapi.json",
            "swagger.yaml",
            "swagger.json",
            "docs/api.md",
            "docs/API.md",
            "docs/openapi.yaml",
            "CONTRIBUTING.md",
            "ARCHITECTURE.md",
            "docs/README.md"
    );

    public void processWebhookEvent(WebhookPayload payload) {
        if (payload.getCommits() == null || payload.getCommits().isEmpty()) {
            return;
        }

        String repoName = payload.getRepository().getFull_name();

        for (WebhookPayload.CommitPayload commit : payload.getCommits()) {
            // Save Event
            WebhookEvent event = new WebhookEvent();
            event.setEventType("push");
            event.setCommitSha(commit.getId());
            event.setRepositoryName(repoName);
            event.setPayload(commit.getMessage());
            event = webhookEventRepository.save(event);

            final Long eventId = event.getId();

            gitHubService.getCommitDiff(repoName, commit.getId())
                    .flatMap(diff -> {
                        if (diff == null || diff.isBlank()) {
                            return Mono.empty();
                        }
                        // Perform semantic and contract analysis across candidate documentation and spec files
                        return reactor.core.publisher.Flux.fromIterable(DOC_CANDIDATES)
                                .flatMap(docPath -> gitHubService.getFileContent(repoName, docPath, payload.getRef())
                                        .filter(content -> content != null && !content.isBlank())
                                        .flatMap(docContent -> geminiLlmService.analyzeDrift(docPath, diff, docContent)
                                                .map(json -> new DocAnalysisResult(docPath, json)))
                                )
                                .collectList();
                    })
                    .subscribe(results -> {
                        try {
                            DriftReport report = new DriftReport();
                            report.setCommitSha(commit.getId());
                            report.setRepositoryName(repoName);

                            List<DriftSuggestion> allSuggestions = new java.util.ArrayList<>();

                            for (DocAnalysisResult res : results) {
                                if (res.json != null && !res.json.isBlank() && !res.json.equals("[]")) {
                                    List<LlmSuggestionDto> dtos = objectMapper.readValue(
                                            res.json,
                                            new TypeReference<List<LlmSuggestionDto>>() {}
                                    );

                                    for (LlmSuggestionDto dto : dtos) {
                                        DriftSuggestion s = new DriftSuggestion();
                                        s.setFilePath(dto.getFilePath() != null && !dto.getFilePath().isBlank() ? dto.getFilePath() : res.docPath);
                                        s.setDriftType(dto.getDriftType() != null ? dto.getDriftType() : "BEHAVIORAL_LOGIC_DRIFT");
                                        s.setSeverity(dto.getSeverity() != null ? dto.getSeverity() : (Boolean.TRUE.equals(dto.getIsBreakingChange()) ? "CRITICAL" : "MEDIUM"));
                                        s.setConfidenceScore(dto.getConfidenceScore() != null ? dto.getConfidenceScore() : 0.90);
                                        s.setImpactedSymbol(dto.getImpactedSymbol() != null ? dto.getImpactedSymbol() : "General Codebase");
                                        s.setIsBreakingChange(Boolean.TRUE.equals(dto.getIsBreakingChange()));
                                        s.setHttpMethod(dto.getHttpMethod());
                                        s.setEndpointPath(dto.getEndpointPath());
                                        s.setSchemaFormat(dto.getSchemaFormat() != null ? dto.getSchemaFormat() : detectSchemaFormat(res.docPath));
                                        s.setOldText(dto.getOldText());
                                        s.setSuggestedText(dto.getSuggestedText());
                                        s.setReason(dto.getReason());
                                        s.setReport(report);
                                        allSuggestions.add(s);
                                    }
                                }
                            }

                            if (!allSuggestions.isEmpty()) {
                                report.setSuggestions(allSuggestions);
                                driftReportRepository.save(report);
                            }

                            WebhookEvent ev = webhookEventRepository.findById(eventId).orElse(null);
                            if (ev != null) {
                                ev.setStatus("PROCESSED");
                                webhookEventRepository.save(ev);
                            }

                        } catch (Exception e) {
                            WebhookEvent ev = webhookEventRepository.findById(eventId).orElse(null);
                            if (ev != null) {
                                ev.setStatus("ERROR");
                                webhookEventRepository.save(ev);
                            }
                            System.err.println("Failed to parse LLM Response: " + e.getMessage());
                        }
                    }, error -> {
                        WebhookEvent ev = webhookEventRepository.findById(eventId).orElse(null);
                        if (ev != null) {
                            ev.setStatus("ERROR");
                            webhookEventRepository.save(ev);
                        }
                        System.err.println("Failed LLM Pipeline: " + error.getMessage());
                    });
        }
    }

    private String detectSchemaFormat(String filePath) {
        if (filePath == null) return "GENERAL_PROSE";
        String lower = filePath.toLowerCase();
        if (lower.contains("openapi") && (lower.endsWith(".yaml") || lower.endsWith(".yml"))) {
            return "OPENAPI_YAML";
        }
        if (lower.contains("openapi") && lower.endsWith(".json")) {
            return "OPENAPI_JSON";
        }
        if (lower.contains("swagger") && (lower.endsWith(".yaml") || lower.endsWith(".yml"))) {
            return "SWAGGER_YAML";
        }
        if (lower.contains("swagger") && lower.endsWith(".json")) {
            return "SWAGGER_JSON";
        }
        if (lower.endsWith(".md") && (lower.contains("api") || lower.contains("schema"))) {
            return "MARKDOWN_TABLE";
        }
        return "GENERAL_PROSE";
    }

    private static class DocAnalysisResult {
        final String docPath;
        final String json;

        DocAnalysisResult(String docPath, String json) {
            this.docPath = docPath;
            this.json = json;
        }
    }
}
