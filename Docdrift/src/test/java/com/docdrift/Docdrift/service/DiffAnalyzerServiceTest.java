package com.docdrift.Docdrift.service;

import com.docdrift.Docdrift.dto.WebhookPayload;
import com.docdrift.Docdrift.model.DriftReport;
import com.docdrift.Docdrift.model.RepositoryConfig;
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
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
class DiffAnalyzerServiceTest {

    private static final String REPO = "owner/repo";
    private static final String BEFORE = "1111111111111111111111111111111111111111";
    private static final String AFTER = "2222222222222222222222222222222222222222";

    private static final String CODE_DIFF = """
            diff --git a/src/OrderController.java b/src/OrderController.java
            --- a/src/OrderController.java
            +++ b/src/OrderController.java
            -    @GetMapping("/api/orders")
            +    @GetMapping("/api/v2/orders")
            """;

    private static final String SUGGESTION_JSON = """
            [{"filePath":"README.md","driftType":"API_SIGNATURE_MISMATCH","severity":"HIGH",
              "confidenceScore":0.9,"isBreakingChange":true,"oldText":"GET /api/orders",
              "suggestedText":"GET /api/v2/orders","reason":"Route changed"}]""";

    @Autowired private DiffAnalyzerService service;
    @Autowired private RepositoryConfigRepository repositoryConfigRepository;
    @Autowired private WebhookEventRepository webhookEventRepository;
    @Autowired private DriftReportRepository driftReportRepository;
    @Autowired private DriftSuggestionRepository driftSuggestionRepository;

    @MockitoBean private GitHubService gitHubService;
    @MockitoBean private GeminiLlmService geminiLlmService;

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

        when(gitHubService.listFiles(REPO, AFTER)).thenReturn(Mono.just(
                List.of("src/OrderController.java", "README.md", "docs/orders.md", "docs/deploy.md")));
        when(gitHubService.getFileContent(REPO, "README.md", AFTER)).thenReturn(Mono.just("Call GET /api/orders"));
        when(gitHubService.getFileContent(REPO, "docs/orders.md", AFTER)).thenReturn(Mono.just("See /api/orders"));
        when(gitHubService.getFileContent(REPO, "docs/deploy.md", AFTER)).thenReturn(Mono.just("How to deploy"));
    }

    private static WebhookPayload push(String before) {
        WebhookPayload payload = new WebhookPayload();
        payload.setRef("refs/heads/main");
        payload.setBefore(before);
        payload.setAfter(AFTER);
        WebhookPayload.RepositoryPayload repo = new WebhookPayload.RepositoryPayload();
        repo.setFull_name(REPO);
        payload.setRepository(repo);
        WebhookPayload.CommitPayload first = new WebhookPayload.CommitPayload();
        first.setId("aaa");
        WebhookPayload.CommitPayload last = new WebhookPayload.CommitPayload();
        last.setId(AFTER);
        last.setMessage("Rename orders route");
        payload.setCommits(List.of(first, last));
        return payload;
    }

    private RepositoryConfig repo() {
        return repositoryConfigRepository.findFirstByNameIgnoreCase(REPO).orElseThrow();
    }

    private void awaitScanFinished() {
        await().atMost(Duration.ofSeconds(10)).until(() -> !"SCANNING".equals(repo().getStatus()));
    }

    @Test
    void analysesWholePushOnceAndOnlyRelevantDocs() {
        when(gitHubService.getCompareDiff(REPO, BEFORE, AFTER)).thenReturn(Mono.just(CODE_DIFF));
        when(geminiLlmService.analyzeDrift(eq("README.md"), anyString(), anyString())).thenReturn(Mono.just(SUGGESTION_JSON));
        when(geminiLlmService.analyzeDrift(eq("docs/orders.md"), anyString(), anyString())).thenReturn(Mono.just("[]"));

        assertThat(service.processWebhookEvent(push(BEFORE), "delivery-1")).isTrue();
        awaitScanFinished();

        // One compare diff for the push instead of one diff per commit
        verify(gitHubService).getCompareDiff(REPO, BEFORE, AFTER);
        verify(gitHubService, never()).getCommitDiff(anyString(), anyString());
        // deploy.md does not mention anything that changed, so it costs no LLM call
        verify(geminiLlmService, never()).analyzeDrift(eq("docs/deploy.md"), anyString(), anyString());

        assertThat(repo().getStatus()).isEqualTo("DRIFTED");
        List<DriftReport> reports = driftReportRepository.findAll();
        assertThat(reports).hasSize(1);
        assertThat(reports.getFirst().getCommitSha()).isEqualTo(AFTER);
        WebhookEvent event = webhookEventRepository.findAll().getFirst();
        assertThat(event.getStatus()).isEqualTo("PROCESSED");
        assertThat(event.getDeliveryId()).isEqualTo("delivery-1");
    }

    @Test
    void ignoresRedeliveredWebhook() {
        when(gitHubService.getCompareDiff(REPO, BEFORE, AFTER)).thenReturn(Mono.just(CODE_DIFF));
        when(geminiLlmService.analyzeDrift(anyString(), anyString(), anyString())).thenReturn(Mono.just("[]"));

        assertThat(service.processWebhookEvent(push(BEFORE), "delivery-2")).isTrue();
        awaitScanFinished();
        assertThat(service.processWebhookEvent(push(BEFORE), "delivery-2")).isFalse();
        assertThat(webhookEventRepository.findAll()).hasSize(1);
    }

    @Test
    void newBranchPushAnalysesHeadCommit() {
        when(gitHubService.getCommitDiff(REPO, AFTER)).thenReturn(Mono.just(CODE_DIFF));
        when(geminiLlmService.analyzeDrift(anyString(), anyString(), anyString())).thenReturn(Mono.just("[]"));

        service.processWebhookEvent(push("0000000000000000000000000000000000000000"), "delivery-3");
        awaitScanFinished();

        verify(gitHubService).getCommitDiff(REPO, AFTER);
        assertThat(repo().getStatus()).isEqualTo("HEALTHY");
    }

    @Test
    void docsOnlyPushMakesNoLlmCalls() {
        when(gitHubService.getCompareDiff(REPO, BEFORE, AFTER)).thenReturn(Mono.just(
                "diff --git a/README.md b/README.md\n--- a/README.md\n+++ b/README.md\n-old\n+new\n"));

        service.processWebhookEvent(push(BEFORE), "delivery-4");
        awaitScanFinished();

        verify(geminiLlmService, never()).analyzeDrift(any(), any(), any());
        assertThat(repo().getStatus()).isEqualTo("HEALTHY");
    }

    @Test
    void recordsLlmFailureInsteadOfReportingNoDrift() {
        when(gitHubService.getCompareDiff(REPO, BEFORE, AFTER)).thenReturn(Mono.just(CODE_DIFF));
        when(geminiLlmService.analyzeDrift(anyString(), anyString(), anyString()))
                .thenReturn(Mono.error(new IllegalStateException("model not found")));

        service.processWebhookEvent(push(BEFORE), "delivery-5");
        awaitScanFinished();

        assertThat(repo().getStatus()).isEqualTo("ERROR");
        assertThat(repo().getLastError()).contains("model not found");
        assertThat(webhookEventRepository.findAll().getFirst().getStatus()).isEqualTo("ERROR");
    }

    @Test
    void manualScanUsesDefaultBranchHeadAndSkipsAlreadyAnalysedCommit() {
        when(gitHubService.getDefaultBranchHeadSha(REPO)).thenReturn(Mono.just(AFTER));
        when(gitHubService.getCommitDiff(REPO, AFTER)).thenReturn(Mono.just(CODE_DIFF));
        when(geminiLlmService.analyzeDrift(eq("README.md"), anyString(), anyString())).thenReturn(Mono.just(SUGGESTION_JSON));
        when(geminiLlmService.analyzeDrift(eq("docs/orders.md"), anyString(), anyString())).thenReturn(Mono.just("[]"));

        service.scanLatestCommit(repo());
        awaitScanFinished();
        assertThat(repo().getStatus()).isEqualTo("DRIFTED");
        assertThat(repo().getLastScannedAt()).isNotNull();

        // Scanning the same head again does not create a duplicate report
        service.scanLatestCommit(repo());
        awaitScanFinished();
        assertThat(driftReportRepository.findAll()).hasSize(1);
        verify(gitHubService).getCommitDiff(REPO, AFTER);
    }

    @Test
    void manualScanReportsGitHubFailure() {
        when(gitHubService.getDefaultBranchHeadSha(REPO)).thenReturn(Mono.error(new IllegalStateException("401 Unauthorized")));

        service.scanLatestCommit(repo());
        awaitScanFinished();

        assertThat(repo().getStatus()).isEqualTo("ERROR");
        assertThat(repo().getLastError()).contains("401 Unauthorized");
    }
}
