package com.docdrift.Docdrift.service;

import com.docdrift.Docdrift.dto.PullRequestPayload;
import com.docdrift.Docdrift.dto.WebhookPayload;
import com.docdrift.Docdrift.dto.WebhookResult;
import com.docdrift.Docdrift.model.DriftReport;
import com.docdrift.Docdrift.model.DriftSuggestion;
import com.docdrift.Docdrift.model.EventStatus;
import com.docdrift.Docdrift.model.RepositoryConfig;
import com.docdrift.Docdrift.model.RepositoryStatus;
import com.docdrift.Docdrift.model.Severity;
import com.docdrift.Docdrift.model.SuggestionSource;
import com.docdrift.Docdrift.model.WebhookEvent;
import com.docdrift.Docdrift.repository.DriftReportRepository;
import com.docdrift.Docdrift.repository.DriftSuggestionRepository;
import com.docdrift.Docdrift.repository.RepositoryConfigRepository;
import com.docdrift.Docdrift.repository.WebhookEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Section 4 analysis features: PR checks, settings, the contract check and alerts. */
@SpringBootTest
class AnalysisFeaturesTest {

    private static final String REPO = "owner/api";
    private static final String BASE = "1111111111111111111111111111111111111111";
    private static final String HEAD = "2222222222222222222222222222222222222222";

    private static final String CONTROLLER_DIFF = """
            diff --git a/src/OrderController.java b/src/OrderController.java
            --- a/src/OrderController.java
            +++ b/src/OrderController.java
            -    @GetMapping("/legacy")
            +    @PostMapping("/bulk")
            """;
    private static final String CONTROLLER_BEFORE = """
            @RestController @RequestMapping("/api/orders") class OrderController {
                @GetMapping public List<Order> list() { return null; }
                @GetMapping("/legacy") public List<Order> legacy() { return null; }
            }""";
    private static final String CONTROLLER_AFTER = """
            @RestController @RequestMapping("/api/orders") class OrderController {
                @GetMapping public List<Order> list() { return null; }
                @PostMapping("/bulk") public void bulk() { }
            }""";
    private static final String SPEC = """
            openapi: 3.0.0
            paths:
              /api/orders:
                get: {}
              /api/orders/legacy:
                get: {}
            """;

    @Autowired private DiffAnalyzerService service;
    @Autowired private RepositoryConfigRepository repositoryConfigRepository;
    @Autowired private WebhookEventRepository webhookEventRepository;
    @Autowired private DriftReportRepository driftReportRepository;
    @Autowired private DriftSuggestionRepository driftSuggestionRepository;
    @Autowired private TransactionTemplate transactionTemplate;

    @MockitoBean private GitHubService gitHubService;
    @MockitoBean private LlmRouter llmRouter;
    @MockitoBean private AlertService alertService;

    @BeforeEach
    void setUp() {
        driftSuggestionRepository.deleteAll();
        driftReportRepository.deleteAll();
        webhookEventRepository.deleteAll();
        repositoryConfigRepository.deleteAll();

        RepositoryConfig config = new RepositoryConfig();
        config.setName(REPO);
        config.setUrl("https://github.com/" + REPO);
        repositoryConfigRepository.save(config);

        when(gitHubService.getCompareDiff(REPO, BASE, HEAD)).thenReturn(Mono.just(CONTROLLER_DIFF));
        when(gitHubService.listFiles(REPO, HEAD)).thenReturn(Mono.just(
                List.of("src/OrderController.java", "openapi.yaml", "README.md", "docs/notes.md")));
        when(gitHubService.getFileContent(REPO, "openapi.yaml", HEAD)).thenReturn(Mono.just(SPEC));
        when(gitHubService.getFileContent(REPO, "README.md", HEAD)).thenReturn(Mono.just("# Orders API"));
        when(gitHubService.getFileContent(REPO, "docs/notes.md", HEAD)).thenReturn(Mono.just("Unrelated notes"));
        when(gitHubService.getFileContent(REPO, "src/OrderController.java", BASE)).thenReturn(Mono.just(CONTROLLER_BEFORE));
        when(gitHubService.getFileContent(REPO, "src/OrderController.java", HEAD)).thenReturn(Mono.just(CONTROLLER_AFTER));
        when(gitHubService.upsertPullRequestComment(anyString(), anyInt(), anyString(), anyString())).thenReturn(Mono.empty());
        when(gitHubService.createCommitStatus(anyString(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(Mono.empty());
        when(llmRouter.analyzeDrift(anyString(), anyString(), anyString(), anyList())).thenReturn(Mono.just("[]"));
        when(alertService.alertIfNeeded(any(), any(), any(), anyList())).thenReturn(List.of());
    }

    private RepositoryConfig repo() {
        return repositoryConfigRepository.findFirstByNameIgnoreCase(REPO).orElseThrow();
    }

    private void updateSettings(java.util.function.Consumer<RepositoryConfig> change) {
        RepositoryConfig config = repo();
        change.accept(config);
        repositoryConfigRepository.save(config);
    }

    private static WebhookPayload push(String branch) {
        WebhookPayload payload = new WebhookPayload();
        payload.setRef("refs/heads/" + branch);
        payload.setBefore(BASE);
        payload.setAfter(HEAD);
        WebhookPayload.RepositoryPayload repository = new WebhookPayload.RepositoryPayload();
        repository.setFull_name(REPO);
        repository.setDefault_branch("main");
        payload.setRepository(repository);
        WebhookPayload.CommitPayload commit = new WebhookPayload.CommitPayload();
        commit.setId(HEAD);
        payload.setCommits(List.of(commit));
        return payload;
    }

    private static PullRequestPayload pullRequest(String action, String headRepo) {
        PullRequestPayload payload = new PullRequestPayload();
        payload.setAction(action);
        payload.setNumber(42);
        WebhookPayload.RepositoryPayload repository = new WebhookPayload.RepositoryPayload();
        repository.setFull_name(REPO);
        repository.setDefault_branch("main");
        payload.setRepository(repository);

        PullRequestPayload.Ref head = new PullRequestPayload.Ref();
        head.setSha(HEAD);
        head.setRef("feature/bulk-orders");
        WebhookPayload.RepositoryPayload headRepository = new WebhookPayload.RepositoryPayload();
        headRepository.setFull_name(headRepo);
        head.setRepo(headRepository);
        PullRequestPayload.Ref base = new PullRequestPayload.Ref();
        base.setSha(BASE);
        base.setRef("main");

        PullRequestPayload.PullRequest pr = new PullRequestPayload.PullRequest();
        pr.setHead(head);
        pr.setBase(base);
        payload.setPull_request(pr);
        return payload;
    }

    private WebhookEvent awaitEventDone() {
        await().atMost(Duration.ofSeconds(10)).until(() -> webhookEventRepository.findAll().stream()
                .allMatch(e -> e.getStatus() != EventStatus.PENDING));
        return webhookEventRepository.findAll().getFirst();
    }

    private List<DriftSuggestion> savedSuggestions() {
        return transactionTemplate.execute(status -> driftReportRepository.findAll().stream()
                .flatMap(r -> r.getSuggestions().stream()).toList());
    }

    @Test
    void contractCheckFindsRemovedAndUndocumentedEndpointsAndTellsTheLlm() {
        assertThat(service.processWebhookEvent(push("main"), "d-1").outcome()).isEqualTo(WebhookResult.Outcome.STARTED);
        awaitEventDone();

        // The verified facts are passed to the LLM for the spec
        verify(llmRouter).analyzeDrift(eq("openapi.yaml"), anyString(), anyString(), argThat(facts ->
                facts.contains("GET /api/orders/legacy was removed from src/OrderController.java but is still documented in openapi.yaml.")
                        && facts.contains("POST /api/orders/bulk was added in src/OrderController.java but is not documented in openapi.yaml.")));

        // The LLM returned nothing, so both findings are saved as rule-based suggestions
        List<DriftSuggestion> suggestions = savedSuggestions();
        assertThat(suggestions).hasSize(2).allMatch(s -> s.getSource() == SuggestionSource.RULE && s.getOldText() == null);
        assertThat(suggestions).anyMatch(s -> s.getImpactedSymbol().equals("GET /api/orders/legacy") && s.getIsBreakingChange());
        assertThat(repo().getStatus()).isEqualTo(RepositoryStatus.DRIFTED);
    }

    @Test
    void llmSuggestionCoveringAFindingReplacesTheRuleSuggestion() {
        when(llmRouter.analyzeDrift(eq("openapi.yaml"), anyString(), anyString(), anyList())).thenReturn(Mono.just("""
                [{"filePath":"openapi.yaml","driftType":"API_CONTRACT_BREAKING_CHANGE","severity":"HIGH",
                  "confidenceScore":0.95,"isBreakingChange":true,"httpMethod":"GET","endpointPath":"/orders/legacy",
                  "oldText":"  /api/orders/legacy:\\n    get: {}\\n","suggestedText":"","reason":"Endpoint removed"}]"""));

        service.processWebhookEvent(push("main"), "d-2");
        awaitEventDone();

        List<DriftSuggestion> suggestions = savedSuggestions();
        assertThat(suggestions).hasSize(2);
        assertThat(suggestions).filteredOn(s -> s.getSource() == SuggestionSource.AI).hasSize(1);
        assertThat(suggestions).filteredOn(s -> s.getSource() == SuggestionSource.RULE)
                .singleElement().satisfies(s -> assertThat(s.getImpactedSymbol()).isEqualTo("POST /api/orders/bulk"));
    }

    @Test
    void pushToUnwatchedBranchIsIgnoredAndRecorded() {
        WebhookResult result = service.processWebhookEvent(push("feature/x"), "d-3");

        assertThat(result.outcome()).isEqualTo(WebhookResult.Outcome.IGNORED);
        assertThat(result.message()).contains("branch 'feature/x' is not watched");
        WebhookEvent event = webhookEventRepository.findAll().getFirst();
        assertThat(event.getStatus()).isEqualTo(EventStatus.IGNORED);
        verify(gitHubService, never()).getCompareDiff(anyString(), anyString(), anyString());

        updateSettings(c -> c.setWatchedBranches(List.of("feature/*")));
        assertThat(service.processWebhookEvent(push("feature/x"), "d-4").outcome()).isEqualTo(WebhookResult.Outcome.STARTED);
        awaitEventDone();
    }

    @Test
    void thresholdsAndDocPathsComeFromSettings() {
        updateSettings(c -> {
            c.setMinSeverity(Severity.HIGH);
            c.setDocPaths(List.of("openapi.yaml"));
        });

        service.processWebhookEvent(push("main"), "d-5");
        awaitEventDone();

        // Only the configured doc is analysed, and the MEDIUM rule finding is filtered out
        verify(llmRouter, never()).analyzeDrift(eq("README.md"), anyString(), anyString(), anyList());
        assertThat(savedSuggestions()).singleElement()
                .satisfies(s -> assertThat(s.getSeverity()).isEqualTo(Severity.HIGH));
        verify(alertService).alertIfNeeded(any(), eq(HEAD), isNull(), argThat(list -> list.size() == 1));
    }

    @Test
    void pullRequestCheckCommentsSetsStatusAndLeavesRepositoryStatusAlone() {
        WebhookResult result = service.processPullRequestEvent(pullRequest("opened", REPO), "d-6");
        assertThat(result.outcome()).isEqualTo(WebhookResult.Outcome.STARTED);
        WebhookEvent event = awaitEventDone();

        assertThat(event.getStatus()).isEqualTo(EventStatus.PROCESSED);
        assertThat(event.getPullRequestNumber()).isEqualTo(42);
        verify(gitHubService).upsertPullRequestComment(eq(REPO), eq(42), eq(PullRequestReport.MARKER),
                argThat(body -> body.contains("Found **2**")));
        verify(gitHubService).createCommitStatus(eq(REPO), eq(HEAD), eq("failure"), eq(PullRequestReport.STATUS_CONTEXT),
                anyString(), any());

        DriftReport report = driftReportRepository.findAll().getFirst();
        assertThat(report.getPullRequestNumber()).isEqualTo(42);
        assertThat(report.getHeadBranch()).isEqualTo("feature/bulk-orders");
        assertThat(repo().getStatus()).isEqualTo(RepositoryStatus.HEALTHY);
    }

    @Test
    void forkPullRequestHasNoCommittableBranchAndClosedPullRequestsAreIgnored() {
        service.processPullRequestEvent(pullRequest("synchronize", "someone/fork"), "d-7");
        awaitEventDone();
        assertThat(driftReportRepository.findAll().getFirst().getHeadBranch()).isNull();

        WebhookResult closed = service.processPullRequestEvent(pullRequest("closed", REPO), "d-8");
        assertThat(closed.outcome()).isEqualTo(WebhookResult.Outcome.IGNORED);

        updateSettings(c -> c.setAnalyzePullRequests(false));
        assertThat(service.processPullRequestEvent(pullRequest("opened", REPO), "d-9").message())
                .contains("pull request checks are turned off");
    }
}
