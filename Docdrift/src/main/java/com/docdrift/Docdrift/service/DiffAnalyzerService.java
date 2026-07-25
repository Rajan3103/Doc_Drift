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
                        if (diff == null || diff.isEmpty()) {
                            return Mono.empty();
                        }
                        // For MVP, just pull README.md to check if the code changes break it
                        return gitHubService.getFileContent(repoName, "README.md", payload.getRef())
                                .flatMap(docContent -> geminiLlmService.analyzeDrift(diff, docContent));
                    })
                    .subscribe(llmResponse -> {
                        try {
                            List<LlmSuggestionDto> suggestionsDto = objectMapper.readValue(llmResponse, new TypeReference<List<LlmSuggestionDto>>() {});
                            
                            if (!suggestionsDto.isEmpty()) {
                                DriftReport report = new DriftReport();
                                report.setCommitSha(commit.getId());
                                report.setRepositoryName(repoName);
                                
                                List<DriftSuggestion> suggestions = suggestionsDto.stream().map(dto -> {
                                    DriftSuggestion s = new DriftSuggestion();
                                    s.setFilePath("README.md");
                                    s.setOldText(dto.getOldText());
                                    s.setSuggestedText(dto.getSuggestedText());
                                    s.setReason(dto.getReason());
                                    s.setReport(report);
                                    return s;
                                }).collect(Collectors.toList());
                                
                                report.setSuggestions(suggestions);
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
}
