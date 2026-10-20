package com.docdrift.Docdrift.service;

import com.docdrift.Docdrift.dto.LlmSuggestionDto;
import com.docdrift.Docdrift.dto.WebhookPayload;
import com.docdrift.Docdrift.model.DriftReport;
import com.docdrift.Docdrift.model.DriftSuggestion;
import com.docdrift.Docdrift.model.DriftType;
import com.docdrift.Docdrift.model.EventStatus;
import com.docdrift.Docdrift.model.RepositoryConfig;
import com.docdrift.Docdrift.model.RepositoryStatus;
import com.docdrift.Docdrift.model.Severity;
import com.docdrift.Docdrift.model.SuggestionStatus;
import com.docdrift.Docdrift.model.WebhookEvent;
import com.docdrift.Docdrift.repository.DriftReportRepository;
import com.docdrift.Docdrift.repository.DriftSuggestionRepository;
import com.docdrift.Docdrift.repository.RepositoryConfigRepository;
import com.docdrift.Docdrift.repository.WebhookEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class DiffAnalyzerService {

    private static final Logger log = LoggerFactory.getLogger(DiffAnalyzerService.class);

    private static final String ZERO_SHA = "0000000000000000000000000000000000000000";

    private final GitHubService gitHubService;
    private final GeminiLlmService geminiLlmService;
    private final DriftReportRepository driftReportRepository;
    private final DriftSuggestionRepository driftSuggestionRepository;
    private final WebhookEventRepository webhookEventRepository;
    private final RepositoryConfigRepository repositoryConfigRepository;
    private final ObjectMapper objectMapper;

    @Value("${docdrift.analysis.max-diff-chars:60000}")
    private int maxDiffChars;
    @Value("${docdrift.analysis.max-file-diff-chars:15000}")
    private int maxFileDiffChars;
    @Value("${docdrift.analysis.max-doc-candidates:25}")
    private int maxDocCandidates;
    @Value("${docdrift.analysis.max-docs:8}")
    private int maxDocs;
    @Value("${docdrift.analysis.max-doc-chars:100000}")
    private int maxDocChars;
    @Value("${docdrift.analysis.llm-concurrency:2}")
    private int llmConcurrency;

    public DiffAnalyzerService(GitHubService gitHubService,
                               GeminiLlmService geminiLlmService,
                               DriftReportRepository driftReportRepository,
                               DriftSuggestionRepository driftSuggestionRepository,
                               WebhookEventRepository webhookEventRepository,
                               RepositoryConfigRepository repositoryConfigRepository,
                               ObjectMapper objectMapper) {
        this.gitHubService = gitHubService;
        this.geminiLlmService = geminiLlmService;
        this.driftReportRepository = driftReportRepository;
        this.driftSuggestionRepository = driftSuggestionRepository;
        this.webhookEventRepository = webhookEventRepository;
        this.repositoryConfigRepository = repositoryConfigRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Records a push event and starts analysing it in the background.
     * Returns false if this delivery was already received (GitHub redelivers on timeouts and on request).
     */
    public boolean processWebhookEvent(WebhookPayload payload, String deliveryId) {
        if (deliveryId != null && webhookEventRepository.existsByDeliveryId(deliveryId)) {
            return false;
        }

        String repoName = payload.getRepository().getFull_name();
        List<WebhookPayload.CommitPayload> commits = payload.getCommits() == null ? List.of() : payload.getCommits();
        String headSha = isRealSha(payload.getAfter()) ? payload.getAfter()
                : commits.isEmpty() ? null : commits.getLast().getId();

        WebhookEvent event = new WebhookEvent();
        event.setDeliveryId(deliveryId);
        event.setEventType("push");
        event.setCommitSha(headSha);
        event.setRepositoryName(repoName);
        event.setPayload(commits.isEmpty() ? null : commits.getLast().getMessage());
        try {
            event = webhookEventRepository.save(event);
        } catch (DataIntegrityViolationException e) {
            return false; // the same delivery arrived concurrently
        }

        if (headSha == null || commits.isEmpty()) {
            // Branch deletion or a push without commits: nothing to analyse
            event.setStatus(EventStatus.PROCESSED);
            webhookEventRepository.save(event);
            return true;
        }

        // One combined diff for the whole push instead of one analysis per commit
        Mono<String> diff = isRealSha(payload.getBefore())
                ? gitHubService.getCompareDiff(repoName, payload.getBefore(), headSha)
                : gitHubService.getCommitDiff(repoName, headSha);

        markScanning(repoName);
        analyze(repoName, event.getId(), headSha, diff).subscribe();
        return true;
    }

    /** Analyses the latest commit on the repository's default branch in the background. */
    public void scanLatestCommit(RepositoryConfig config) {
        String repoName = config.getName();
        markScanning(repoName);

        gitHubService.getDefaultBranchHeadSha(repoName)
                .publishOn(Schedulers.boundedElastic())
                .flatMap(sha -> {
                    if (driftReportRepository.existsByRepositoryNameIgnoreCaseAndCommitSha(repoName, sha)) {
                        // Already analysed; avoid paying for the same LLM calls and creating a duplicate report
                        finish(repoName, null, null, false);
                        return Mono.<Void>empty();
                    }
                    WebhookEvent event = new WebhookEvent();
                    event.setEventType("manual_scan");
                    event.setCommitSha(sha);
                    event.setRepositoryName(repoName);
                    event = webhookEventRepository.save(event);
                    return analyze(repoName, event.getId(), sha, gitHubService.getCommitDiff(repoName, sha));
                })
                .onErrorResume(error -> Mono.fromRunnable(() ->
                                finish(repoName, null, "Could not read the repository from GitHub: " + describe(error), true))
                        .subscribeOn(Schedulers.boundedElastic()).then())
                .subscribe();
    }

    /** Runs the analysis pipeline. Never errors: failures are recorded on the event and the repository. */
    Mono<Void> analyze(String repoName, Long eventId, String sha, Mono<String> rawDiff) {
        return rawDiff
                .map(raw -> DiffPreprocessor.process(raw, maxDiffChars, maxFileDiffChars))
                .flatMap(diff -> {
                    if (!diff.hasCodeChanges()) {
                        log.info("Skipping {}@{}: no code changes outside docs", repoName, sha);
                        return Mono.just(List.<DocAnalysisResult>of());
                    }
                    Set<String> keywords = DocSelector.keywords(diff.diff(), diff.changedFiles());
                    return gitHubService.listFiles(repoName, sha)
                            .flatMapMany(paths -> Flux.fromIterable(DocSelector.candidates(paths, maxDocCandidates)))
                            .concatMap(path -> gitHubService.getFileContent(repoName, path, sha)
                                    .map(content -> new DocFile(path, content)))
                            .filter(doc -> !doc.content().isBlank() && DocSelector.isRelevant(doc.path(), doc.content(), keywords))
                            .take(maxDocs)
                            .flatMap(doc -> geminiLlmService.analyzeDrift(doc.path(), diff.diff(), truncateDoc(doc.content()))
                                    .map(json -> new DocAnalysisResult(doc.path(), json, null))
                                    .onErrorResume(e -> Mono.just(new DocAnalysisResult(doc.path(), null, describe(e)))),
                                    llmConcurrency)
                            .collectList();
                })
                .publishOn(Schedulers.boundedElastic())
                .doOnNext(results -> saveResults(repoName, eventId, sha, results))
                .then()
                .onErrorResume(error -> Mono.fromRunnable(() -> {
                    log.error("Drift analysis failed for {}@{}", repoName, sha, error);
                    finish(repoName, eventId, describe(error), true);
                }).subscribeOn(Schedulers.boundedElastic()).then());
    }

    private void saveResults(String repoName, Long eventId, String sha, List<DocAnalysisResult> results) {
        DriftReport report = new DriftReport();
        report.setCommitSha(sha);
        report.setRepositoryName(repoName);

        List<DriftSuggestion> allSuggestions = new ArrayList<>();
        List<String> failures = new ArrayList<>();

        for (DocAnalysisResult res : results) {
            if (res.error() != null) {
                failures.add(res.docPath() + ": " + res.error());
                continue;
            }
            if (res.json() == null || res.json().isBlank() || res.json().equals("[]")) {
                continue;
            }
            List<LlmSuggestionDto> dtos;
            try {
                dtos = objectMapper.readValue(res.json(), new TypeReference<List<LlmSuggestionDto>>() {});
            } catch (RuntimeException e) {
                failures.add(res.docPath() + ": could not parse LLM response (" + e.getMessage() + ")");
                continue;
            }
            for (LlmSuggestionDto dto : dtos) {
                allSuggestions.add(toSuggestion(dto, res.docPath(), report));
            }
        }

        if (!allSuggestions.isEmpty()) {
            report.setSuggestions(allSuggestions);
            driftReportRepository.save(report);
        }

        boolean allFailed = !results.isEmpty() && failures.size() == results.size();
        finish(repoName, eventId, failures.isEmpty() ? null : String.join("\n", failures), allFailed);
    }

    private DriftSuggestion toSuggestion(LlmSuggestionDto dto, String docPath, DriftReport report) {
        DriftSuggestion s = new DriftSuggestion();
        s.setFilePath(dto.getFilePath() != null && !dto.getFilePath().isBlank() ? dto.getFilePath() : docPath);
        DriftType driftType = DriftType.parse(dto.getDriftType());
        Severity severity = Severity.parse(dto.getSeverity());
        s.setDriftType(driftType != null ? driftType : DriftType.BEHAVIORAL_LOGIC_DRIFT);
        s.setSeverity(severity != null ? severity : (Boolean.TRUE.equals(dto.getIsBreakingChange()) ? Severity.CRITICAL : Severity.MEDIUM));
        s.setConfidenceScore(dto.getConfidenceScore() != null ? dto.getConfidenceScore() : 0.90);
        s.setImpactedSymbol(dto.getImpactedSymbol() != null ? dto.getImpactedSymbol() : "General Codebase");
        s.setIsBreakingChange(Boolean.TRUE.equals(dto.getIsBreakingChange()));
        s.setHttpMethod(dto.getHttpMethod());
        s.setEndpointPath(dto.getEndpointPath());
        s.setSchemaFormat(dto.getSchemaFormat() != null ? dto.getSchemaFormat() : detectSchemaFormat(docPath));
        s.setOldText(dto.getOldText());
        s.setSuggestedText(dto.getSuggestedText());
        s.setReason(dto.getReason());
        s.setReport(report);
        return s;
    }

    private void markScanning(String repoName) {
        repositoryConfigRepository.findFirstByNameIgnoreCase(repoName).ifPresent(config -> {
            config.setStatus(RepositoryStatus.SCANNING);
            repositoryConfigRepository.save(config);
        });
    }

    /** Records the outcome on the event and sets the repository's status for the dashboard. */
    private void finish(String repoName, Long eventId, String errorMessage, boolean failed) {
        if (eventId != null) {
            webhookEventRepository.findById(eventId).ifPresent(ev -> {
                ev.setStatus(failed ? EventStatus.ERROR : EventStatus.PROCESSED);
                ev.setErrorMessage(errorMessage);
                webhookEventRepository.save(ev);
            });
        }
        repositoryConfigRepository.findFirstByNameIgnoreCase(repoName).ifPresent(config -> {
            config.setLastScannedAt(LocalDateTime.now());
            config.setLastError(errorMessage);
            config.setStatus(failed ? RepositoryStatus.ERROR : statusFromPendingSuggestions(repoName));
            repositoryConfigRepository.save(config);
        });
    }

    /** DRIFTED while any suggestion for the repository still awaits review, otherwise HEALTHY. */
    public RepositoryStatus statusFromPendingSuggestions(String repoName) {
        return driftSuggestionRepository.existsByStatusAndReport_RepositoryNameIgnoreCase(SuggestionStatus.PENDING, repoName)
                ? RepositoryStatus.DRIFTED : RepositoryStatus.HEALTHY;
    }

    private String truncateDoc(String content) {
        if (content.length() <= maxDocChars) {
            return content;
        }
        return content.substring(0, maxDocChars) + "\n... [document truncated]";
    }

    private static boolean isRealSha(String sha) {
        return sha != null && !sha.isBlank() && !ZERO_SHA.equals(sha);
    }

    private static String describe(Throwable error) {
        return error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName();
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

    private record DocFile(String path, String content) {
    }

    private record DocAnalysisResult(String docPath, String json, String error) {
    }
}
