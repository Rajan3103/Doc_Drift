package com.docdrift.Docdrift.service;

import com.docdrift.Docdrift.dto.LlmSuggestionDto;
import com.docdrift.Docdrift.dto.PullRequestPayload;
import com.docdrift.Docdrift.dto.WebhookPayload;
import com.docdrift.Docdrift.dto.WebhookResult;
import com.docdrift.Docdrift.model.DriftReport;
import com.docdrift.Docdrift.model.DriftSuggestion;
import com.docdrift.Docdrift.model.DriftType;
import com.docdrift.Docdrift.model.EventStatus;
import com.docdrift.Docdrift.model.RepositoryConfig;
import com.docdrift.Docdrift.model.RepositoryStatus;
import com.docdrift.Docdrift.model.Severity;
import com.docdrift.Docdrift.model.SuggestionSource;
import com.docdrift.Docdrift.model.SuggestionStatus;
import com.docdrift.Docdrift.model.WebhookEvent;
import com.docdrift.Docdrift.repository.DriftReportRepository;
import com.docdrift.Docdrift.repository.DriftSuggestionRepository;
import com.docdrift.Docdrift.repository.RepositoryConfigRepository;
import com.docdrift.Docdrift.repository.WebhookEventRepository;
import com.docdrift.Docdrift.service.ApiContractChecker.Endpoint;
import com.docdrift.Docdrift.service.ApiContractChecker.Finding;
import com.docdrift.Docdrift.service.ApiContractChecker.FindingKind;
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

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class DiffAnalyzerService {

    private static final Logger log = LoggerFactory.getLogger(DiffAnalyzerService.class);

    private static final String ZERO_SHA = "0000000000000000000000000000000000000000";
    private static final Set<String> ANALYSED_PR_ACTIONS = Set.of("opened", "synchronize", "reopened", "ready_for_review");
    private static final int MAX_CONTRACT_SOURCE_FILES = 20;

    private final GitHubService gitHubService;
    private final LlmRouter llmRouter;
    private final AlertService alertService;
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
    @Value("${docdrift.dashboard-url:}")
    private String dashboardUrl;

    public DiffAnalyzerService(GitHubService gitHubService,
                               LlmRouter llmRouter,
                               AlertService alertService,
                               DriftReportRepository driftReportRepository,
                               DriftSuggestionRepository driftSuggestionRepository,
                               WebhookEventRepository webhookEventRepository,
                               RepositoryConfigRepository repositoryConfigRepository,
                               ObjectMapper objectMapper) {
        this.gitHubService = gitHubService;
        this.llmRouter = llmRouter;
        this.alertService = alertService;
        this.driftReportRepository = driftReportRepository;
        this.driftSuggestionRepository = driftSuggestionRepository;
        this.webhookEventRepository = webhookEventRepository;
        this.repositoryConfigRepository = repositoryConfigRepository;
        this.objectMapper = objectMapper;
    }

    /** What to analyse. prNumber/prHeadBranch are set for pull request checks; baseSha may be null. */
    record Target(String repoName, Long eventId, String headSha, String baseSha, Integer prNumber, String prHeadBranch) {

        boolean isPullRequest() {
            return prNumber != null;
        }
    }

    /**
     * Records a push event and starts analysing it in the background.
     * Pushes to branches the repository does not watch, and redelivered webhooks, are ignored.
     */
    public WebhookResult processWebhookEvent(WebhookPayload payload, String deliveryId) {
        if (deliveryId != null && webhookEventRepository.existsByDeliveryId(deliveryId)) {
            return WebhookResult.duplicate();
        }

        String repoName = payload.getRepository().getFull_name();
        RepositoryConfig config = repositoryConfigRepository.findFirstByNameIgnoreCase(repoName).orElse(null);
        List<WebhookPayload.CommitPayload> commits = payload.getCommits() == null ? List.of() : payload.getCommits();
        String headSha = isRealSha(payload.getAfter()) ? payload.getAfter()
                : commits.isEmpty() ? null : commits.getLast().getId();

        WebhookEvent event = new WebhookEvent();
        event.setDeliveryId(deliveryId);
        event.setEventType("push");
        event.setCommitSha(headSha);
        event.setRepositoryName(repoName);
        event.setPayload(commits.isEmpty() ? null : commits.getLast().getMessage());

        String branch = payload.getRef() != null && payload.getRef().startsWith("refs/heads/")
                ? payload.getRef().substring("refs/heads/".length()) : null;
        String ignoreReason = null;
        if (headSha == null || commits.isEmpty()) {
            ignoreReason = "no commits to analyse (e.g. a deleted branch)";
        } else if (config != null && !SettingsMatcher.watchesBranch(config.getWatchedBranches(), branch,
                payload.getRepository().getDefault_branch())) {
            ignoreReason = "branch '" + (branch != null ? branch : payload.getRef()) + "' is not watched";
        }
        if (ignoreReason != null) {
            event.setStatus(EventStatus.IGNORED);
            event.setErrorMessage(ignoreReason);
        }

        try {
            event = webhookEventRepository.save(event);
        } catch (DataIntegrityViolationException e) {
            return WebhookResult.duplicate(); // the same delivery arrived concurrently
        }
        if (ignoreReason != null) {
            return WebhookResult.ignored(ignoreReason);
        }

        // One combined diff for the whole push instead of one analysis per commit
        boolean hasBase = isRealSha(payload.getBefore());
        Mono<String> diff = hasBase
                ? gitHubService.getCompareDiff(repoName, payload.getBefore(), headSha)
                : gitHubService.getCommitDiff(repoName, headSha);

        markScanning(repoName);
        Target target = new Target(repoName, event.getId(), headSha, hasBase ? payload.getBefore() : null, null, null);
        analyze(target, config, diff).subscribe();
        return WebhookResult.started();
    }

    /** Analyses a pull request into a watched branch, then comments on it and sets a commit status. */
    public WebhookResult processPullRequestEvent(PullRequestPayload payload, String deliveryId) {
        if (deliveryId != null && webhookEventRepository.existsByDeliveryId(deliveryId)) {
            return WebhookResult.duplicate();
        }
        PullRequestPayload.PullRequest pr = payload.getPull_request();
        if (pr == null || pr.getHead() == null || pr.getBase() == null || payload.getNumber() == null) {
            return WebhookResult.ignored("pull request payload is incomplete");
        }

        String repoName = payload.getRepository().getFull_name();
        RepositoryConfig config = repositoryConfigRepository.findFirstByNameIgnoreCase(repoName).orElse(null);

        WebhookEvent event = new WebhookEvent();
        event.setDeliveryId(deliveryId);
        event.setEventType("pull_request");
        event.setPullRequestNumber(payload.getNumber());
        event.setCommitSha(pr.getHead().getSha());
        event.setRepositoryName(repoName);
        event.setPayload("Pull request #" + payload.getNumber() + " " + payload.getAction());

        String ignoreReason = null;
        if (!ANALYSED_PR_ACTIONS.contains(payload.getAction())) {
            ignoreReason = "pull request action '" + payload.getAction() + "' needs no analysis";
        } else if (Boolean.TRUE.equals(pr.getDraft())) {
            ignoreReason = "draft pull request";
        } else if (config != null && !Boolean.TRUE.equals(config.getAnalyzePullRequests())) {
            ignoreReason = "pull request checks are turned off for this repository";
        } else if (config != null && !SettingsMatcher.watchesBranch(config.getWatchedBranches(), pr.getBase().getRef(),
                payload.getRepository().getDefault_branch())) {
            ignoreReason = "base branch '" + pr.getBase().getRef() + "' is not watched";
        }
        if (ignoreReason != null) {
            event.setStatus(EventStatus.IGNORED);
            event.setErrorMessage(ignoreReason);
        }

        try {
            event = webhookEventRepository.save(event);
        } catch (DataIntegrityViolationException e) {
            return WebhookResult.duplicate();
        }
        if (ignoreReason != null) {
            return WebhookResult.ignored(ignoreReason);
        }

        // Fixes can only be committed to the PR's branch when it lives in this repository (not a fork)
        boolean sameRepository = pr.getHead().getRepo() != null
                && repoName.equalsIgnoreCase(pr.getHead().getRepo().getFull_name());
        Target target = new Target(repoName, event.getId(), pr.getHead().getSha(), pr.getBase().getSha(),
                payload.getNumber(), sameRepository ? pr.getHead().getRef() : null);
        analyze(target, config, gitHubService.getCompareDiff(repoName, pr.getBase().getSha(), pr.getHead().getSha()))
                .subscribe();
        return WebhookResult.started();
    }

    /** Analyses the latest commit on the repository's default branch in the background. */
    public void scanLatestCommit(RepositoryConfig config) {
        String repoName = config.getName();
        markScanning(repoName);

        gitHubService.getDefaultBranchHeadSha(repoName)
                .publishOn(Schedulers.boundedElastic())
                .flatMap(sha -> {
                    if (driftReportRepository.existsByRepositoryNameIgnoreCaseAndCommitShaAndPullRequestNumberIsNull(repoName, sha)) {
                        // Already analysed; avoid paying for the same LLM calls and creating a duplicate report
                        finish(new Target(repoName, null, sha, null, null, null), null, false);
                        return Mono.<Void>empty();
                    }
                    WebhookEvent event = new WebhookEvent();
                    event.setEventType("manual_scan");
                    event.setCommitSha(sha);
                    event.setRepositoryName(repoName);
                    event = webhookEventRepository.save(event);
                    return analyze(new Target(repoName, event.getId(), sha, null, null, null), config,
                            gitHubService.getCommitDiff(repoName, sha));
                })
                .onErrorResume(error -> Mono.fromRunnable(() ->
                                finish(new Target(repoName, null, null, null, null, null),
                                        "Could not read the repository from GitHub: " + describe(error), true))
                        .subscribeOn(Schedulers.boundedElastic()).then())
                .subscribe();
    }

    /** Runs the analysis pipeline. Never errors: failures are recorded on the event and the repository. */
    Mono<Void> analyze(Target target, RepositoryConfig settings, Mono<String> rawDiff) {
        List<String> docPaths = settings != null ? settings.getDocPaths() : List.of();
        String repoName = target.repoName();
        String sha = target.headSha();

        return rawDiff
                .map(raw -> DiffPreprocessor.process(raw, maxDiffChars, maxFileDiffChars))
                .flatMap(diff -> {
                    if (!diff.hasCodeChanges()) {
                        log.info("Skipping {}@{}: no code changes outside docs", repoName, sha);
                        return Mono.just(List.<DocAnalysisResult>of());
                    }
                    Set<String> keywords = DocSelector.keywords(diff.diff(), diff.changedFiles());
                    Mono<List<DocFile>> docs = gitHubService.listFiles(repoName, sha)
                            .flatMapMany(paths -> Flux.fromIterable(docPaths.isEmpty()
                                    ? DocSelector.candidates(paths, maxDocCandidates)
                                    : paths.stream().filter(p -> SettingsMatcher.matchesDocPath(docPaths, p))
                                            .limit(maxDocCandidates).toList()))
                            .concatMap(path -> gitHubService.getFileContent(repoName, path, sha)
                                    .map(content -> new DocFile(path, content)))
                            // Docs the user listed explicitly are always checked
                            .filter(doc -> !doc.content().isBlank() && (!docPaths.isEmpty()
                                    || DocSelector.isRelevant(doc.path(), doc.content(), keywords)))
                            .take(maxDocs)
                            .collectList();

                    return docs.flatMap(docList -> contractFindings(target, diff.changedFiles(), docList)
                            .flatMap(findingsBySpec -> Flux.fromIterable(docList)
                                    .flatMap(doc -> {
                                        List<Finding> findings = findingsBySpec.getOrDefault(doc.path(), List.of());
                                        List<String> facts = findings.stream().map(f -> f.describe(doc.path())).toList();
                                        return llmRouter.analyzeDrift(doc.path(), diff.diff(), truncateDoc(doc.content()), facts)
                                                .map(json -> new DocAnalysisResult(doc.path(), json, null, findings))
                                                .onErrorResume(e -> Mono.just(new DocAnalysisResult(doc.path(), null, describe(e), findings)));
                                    }, llmConcurrency)
                                    .collectList()));
                })
                .publishOn(Schedulers.boundedElastic())
                .doOnNext(results -> saveResults(target, settings, results))
                .then()
                .onErrorResume(error -> Mono.fromRunnable(() -> {
                    log.error("Drift analysis failed for {}@{}", repoName, sha, error);
                    finish(target, describe(error), true);
                }).subscribeOn(Schedulers.boundedElastic()).then());
    }

    /**
     * Runs the deterministic API contract check for every OpenAPI/Swagger spec among the docs:
     * endpoints in changed controller sources before vs after the change, compared with the spec.
     */
    private Mono<Map<String, List<Finding>>> contractFindings(Target target, List<String> changedFiles, List<DocFile> docs) {
        List<DocFile> specs = docs.stream().filter(doc -> DocSelector.isApiSpec(doc.path())).toList();
        List<String> sources = changedFiles.stream().filter(ApiContractChecker::isControllerSource)
                .limit(MAX_CONTRACT_SOURCE_FILES).toList();
        if (specs.isEmpty() || sources.isEmpty()) {
            return Mono.just(Map.of());
        }

        Mono<String> baseSha = target.baseSha() != null
                ? Mono.just(target.baseSha())
                : gitHubService.getParentSha(target.repoName(), target.headSha());

        Mono<Map<String, Set<Endpoint>>> head = endpointsAt(target.repoName(), sources, Mono.just(target.headSha()));
        Mono<Map<String, Set<Endpoint>>> base = endpointsAt(target.repoName(), sources, baseSha);

        return Mono.zip(base, head).map(tuple -> {
            Map<String, List<Finding>> bySpec = new LinkedHashMap<>();
            for (DocFile spec : specs) {
                try {
                    Set<Endpoint> documented = ApiContractChecker.endpointsInSpec(spec.content());
                    bySpec.put(spec.path(), ApiContractChecker.compare(tuple.getT1(), tuple.getT2(), documented));
                } catch (IllegalArgumentException e) {
                    log.info("Skipping contract check for {}: {}", spec.path(), e.getMessage());
                }
            }
            return bySpec;
        }).onErrorResume(e -> {
            // The contract check is an extra; the LLM analysis still runs without it
            log.warn("Contract check failed for {}@{}", target.repoName(), target.headSha(), e);
            return Mono.just(Map.of());
        });
    }

    private Mono<Map<String, Set<Endpoint>>> endpointsAt(String repoName, List<String> sources, Mono<String> ref) {
        return ref.flatMap(sha -> Flux.fromIterable(sources)
                        .concatMap(path -> gitHubService.getFileContent(repoName, path, sha)
                                .map(content -> Map.entry(path, ApiContractChecker.endpointsInSpringSource(content))))
                        .collectMap(Map.Entry::getKey, Map.Entry::getValue))
                .defaultIfEmpty(Map.of()); // no parent commit: nothing existed before
    }

    private void saveResults(Target target, RepositoryConfig settings, List<DocAnalysisResult> results) {
        DriftReport report = new DriftReport();
        report.setCommitSha(target.headSha());
        report.setRepositoryName(target.repoName());
        report.setPullRequestNumber(target.prNumber());
        report.setHeadBranch(target.prHeadBranch());

        List<DriftSuggestion> allSuggestions = new ArrayList<>();
        List<String> failures = new ArrayList<>();

        for (DocAnalysisResult res : results) {
            List<DriftSuggestion> docSuggestions = new ArrayList<>();
            if (res.error() != null) {
                failures.add(res.docPath() + ": " + res.error());
            } else if (res.json() != null && !res.json().isBlank() && !res.json().equals("[]")) {
                try {
                    List<LlmSuggestionDto> dtos = objectMapper.readValue(res.json(), new TypeReference<List<LlmSuggestionDto>>() {});
                    for (LlmSuggestionDto dto : dtos) {
                        docSuggestions.add(toSuggestion(dto, res.docPath(), report));
                    }
                } catch (RuntimeException e) {
                    failures.add(res.docPath() + ": could not parse LLM response (" + e.getMessage() + ")");
                }
            }
            // Contract findings the LLM did not cover (or could not, because it failed) are still reported
            for (Finding finding : res.findings()) {
                boolean covered = docSuggestions.stream().anyMatch(s ->
                        ApiContractChecker.coveredBy(finding.endpoint(), s.getHttpMethod(), s.getEndpointPath()));
                if (!covered) {
                    docSuggestions.add(ruleSuggestion(finding, res.docPath(), report));
                }
            }
            allSuggestions.addAll(docSuggestions);
        }

        List<DriftSuggestion> kept = allSuggestions.stream().filter(s -> passesThresholds(s, settings)).toList();
        if (!kept.isEmpty()) {
            report.setSuggestions(new ArrayList<>(kept));
            driftReportRepository.save(report);
        }

        List<String> warnings = new ArrayList<>(failures);
        if (settings != null) {
            warnings.addAll(alertService.alertIfNeeded(settings, target.headSha(), target.prNumber(), kept));
        }
        if (target.isPullRequest()) {
            warnings.addAll(reportOnPullRequest(target, kept));
        }

        boolean allFailed = !results.isEmpty() && failures.size() == results.size()
                && results.stream().allMatch(r -> r.findings().isEmpty());
        finish(target, warnings.isEmpty() ? null : String.join("\n", warnings), allFailed);
    }

    private static boolean passesThresholds(DriftSuggestion s, RepositoryConfig settings) {
        if (settings == null) {
            return true;
        }
        Severity minSeverity = settings.getMinSeverity() != null ? settings.getMinSeverity() : Severity.LOW;
        double minConfidence = settings.getMinConfidence() != null ? settings.getMinConfidence() : 0.0;
        // Severity enum is ordered CRITICAL, HIGH, MEDIUM, LOW
        return s.getSeverity().ordinal() <= minSeverity.ordinal()
                && (s.getConfidenceScore() == null || s.getConfidenceScore() >= minConfidence);
    }

    /** Posts or updates the PR comment and sets the commit status. Returns warnings instead of failing. */
    private List<String> reportOnPullRequest(Target target, List<DriftSuggestion> suggestions) {
        List<String> warnings = new ArrayList<>();
        try {
            gitHubService.upsertPullRequestComment(target.repoName(), target.prNumber(), PullRequestReport.MARKER,
                    PullRequestReport.comment(target.headSha(), suggestions, dashboardUrl)).block(Duration.ofSeconds(30));
        } catch (RuntimeException e) {
            log.warn("Could not comment on {}#{}", target.repoName(), target.prNumber(), e);
            warnings.add("Could not comment on the pull request (token needs Pull requests: write): " + describe(e));
        }
        try {
            gitHubService.createCommitStatus(target.repoName(), target.headSha(), PullRequestReport.statusState(suggestions),
                    PullRequestReport.STATUS_CONTEXT, PullRequestReport.statusDescription(suggestions), dashboardUrl)
                    .block(Duration.ofSeconds(30));
        } catch (RuntimeException e) {
            log.warn("Could not set commit status on {}@{}", target.repoName(), target.headSha(), e);
            warnings.add("Could not set the commit status (token needs Commit statuses: write): " + describe(e));
        }
        return warnings;
    }

    private DriftSuggestion toSuggestion(LlmSuggestionDto dto, String docPath, DriftReport report) {
        DriftSuggestion s = new DriftSuggestion();
        s.setFilePath(dto.getFilePath() != null && !dto.getFilePath().isBlank() ? dto.getFilePath() : docPath);
        DriftType driftType = DriftType.parse(dto.getDriftType());
        Severity severity = Severity.parse(dto.getSeverity());
        s.setDriftType(driftType != null ? driftType : DriftType.BEHAVIORAL_LOGIC_DRIFT);
        s.setSeverity(severity != null ? severity : (Boolean.TRUE.equals(dto.getIsBreakingChange()) ? Severity.CRITICAL : Severity.MEDIUM));
        s.setConfidenceScore(dto.getConfidenceScore() != null ? dto.getConfidenceScore() : 0.90);
        s.setImpactedSymbol(dto.getImpactedSymbol() != null && !dto.getImpactedSymbol().isBlank() ? dto.getImpactedSymbol() : "General Codebase");
        s.setIsBreakingChange(Boolean.TRUE.equals(dto.getIsBreakingChange()));
        s.setHttpMethod(blankToNull(dto.getHttpMethod()));
        s.setEndpointPath(blankToNull(dto.getEndpointPath()));
        s.setSchemaFormat(dto.getSchemaFormat() != null ? dto.getSchemaFormat() : detectSchemaFormat(docPath));
        s.setOldText(dto.getOldText());
        s.setSuggestedText(dto.getSuggestedText());
        s.setReason(dto.getReason());
        s.setSource(SuggestionSource.AI);
        s.setReport(report);
        return s;
    }

    /** A suggestion from the deterministic contract check. It has no oldText, so approving it only acknowledges it. */
    private DriftSuggestion ruleSuggestion(Finding finding, String specPath, DriftReport report) {
        boolean removed = finding.kind() == FindingKind.REMOVED_BUT_DOCUMENTED;
        Endpoint endpoint = finding.endpoint();
        DriftSuggestion s = new DriftSuggestion();
        s.setFilePath(specPath);
        s.setSource(SuggestionSource.RULE);
        s.setDriftType(removed ? DriftType.API_CONTRACT_BREAKING_CHANGE : DriftType.SCHEMA_DRIFT);
        s.setSeverity(removed ? Severity.HIGH : Severity.MEDIUM);
        s.setIsBreakingChange(removed);
        s.setConfidenceScore(1.0);
        s.setImpactedSymbol(endpoint.display());
        s.setHttpMethod(endpoint.method());
        s.setEndpointPath(endpoint.path());
        s.setSchemaFormat(detectSchemaFormat(specPath));
        s.setSuggestedText(removed
                ? "Remove the " + endpoint.display() + " operation from " + specPath + " (or restore the endpoint in the code)."
                : "Add the new operation to " + specPath + ", for example:\n"
                        + "  " + endpoint.path() + ":\n"
                        + "    " + endpoint.method().toLowerCase() + ":\n"
                        + "      summary: TODO describe this endpoint\n"
                        + "      responses:\n"
                        + "        '200':\n"
                        + "          description: OK");
        s.setReason(finding.describe(specPath) + " Found by DocDrift's rule-based API contract check.");
        s.setReport(report);
        return s;
    }

    private void markScanning(String repoName) {
        repositoryConfigRepository.findFirstByNameIgnoreCase(repoName).ifPresent(config -> {
            config.setStatus(RepositoryStatus.SCANNING);
            repositoryConfigRepository.save(config);
        });
    }

    /**
     * Records the outcome on the event and, for analyses of watched branches, sets the repository's status.
     * Pull request checks leave the repository status alone: the branch itself has not changed.
     */
    private void finish(Target target, String errorMessage, boolean failed) {
        if (target.eventId() != null) {
            webhookEventRepository.findById(target.eventId()).ifPresent(ev -> {
                ev.setStatus(failed ? EventStatus.ERROR : EventStatus.PROCESSED);
                ev.setErrorMessage(errorMessage);
                webhookEventRepository.save(ev);
            });
        }
        if (target.isPullRequest()) {
            return;
        }
        repositoryConfigRepository.findFirstByNameIgnoreCase(target.repoName()).ifPresent(config -> {
            config.setLastScannedAt(LocalDateTime.now());
            config.setLastError(errorMessage);
            config.setStatus(failed ? RepositoryStatus.ERROR : statusFromPendingSuggestions(target.repoName()));
            repositoryConfigRepository.save(config);
        });
    }

    /** DRIFTED while any suggestion for the repository still awaits review, otherwise HEALTHY. */
    public RepositoryStatus statusFromPendingSuggestions(String repoName) {
        return driftSuggestionRepository.existsByStatusAndReport_RepositoryNameIgnoreCaseAndReport_PullRequestNumberIsNull(
                SuggestionStatus.PENDING, repoName) ? RepositoryStatus.DRIFTED : RepositoryStatus.HEALTHY;
    }

    private String truncateDoc(String content) {
        if (content.length() <= maxDocChars) {
            return content;
        }
        return content.substring(0, maxDocChars) + "\n... [document truncated]";
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
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

    private record DocAnalysisResult(String docPath, String json, String error, List<Finding> findings) {
    }
}
