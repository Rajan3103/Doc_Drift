package com.docdrift.Docdrift.controller;

import com.docdrift.Docdrift.model.DriftReport;
import com.docdrift.Docdrift.model.DriftSuggestion;
import com.docdrift.Docdrift.model.DriftType;
import com.docdrift.Docdrift.model.RepositoryConfig;
import com.docdrift.Docdrift.model.RepositoryStatus;
import com.docdrift.Docdrift.model.Severity;
import com.docdrift.Docdrift.model.SuggestionStatus;
import com.docdrift.Docdrift.repository.DriftReportRepository;
import com.docdrift.Docdrift.repository.DriftSuggestionRepository;
import com.docdrift.Docdrift.repository.RepositoryConfigRepository;
import com.docdrift.Docdrift.repository.WebhookEventRepository;
import com.docdrift.Docdrift.service.GitHubService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ReviewControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private RepositoryConfigRepository repositoryConfigRepository;
    @Autowired private DriftReportRepository driftReportRepository;
    @Autowired private DriftSuggestionRepository driftSuggestionRepository;
    @Autowired private WebhookEventRepository webhookEventRepository;

    @MockitoBean private GitHubService gitHubService;

    private DriftSuggestion alphaCritical;

    @BeforeEach
    void setUp() {
        driftSuggestionRepository.deleteAll();
        driftReportRepository.deleteAll();
        webhookEventRepository.deleteAll();
        repositoryConfigRepository.deleteAll();

        RepositoryConfig alpha = new RepositoryConfig();
        alpha.setName("o/alpha");
        alpha.setUrl("https://github.com/o/alpha");
        alpha.setStatus(RepositoryStatus.DRIFTED);
        repositoryConfigRepository.save(alpha);

        DriftReport older = report("o/alpha", "sha1", LocalDateTime.now().minusDays(1),
                suggestion(Severity.CRITICAL, SuggestionStatus.PENDING, true),
                suggestion(Severity.LOW, SuggestionStatus.REJECTED, false));
        report("o/beta", "sha2", LocalDateTime.now(),
                suggestion(Severity.HIGH, SuggestionStatus.PENDING, false));
        alphaCritical = older.getSuggestions().getFirst();
    }

    private DriftReport report(String repo, String sha, LocalDateTime when, DriftSuggestion... suggestions) {
        DriftReport report = new DriftReport();
        report.setRepositoryName(repo);
        report.setCommitSha(sha);
        report.setAnalyzedAt(when);
        List<DriftSuggestion> list = new ArrayList<>(List.of(suggestions));
        list.forEach(s -> s.setReport(report));
        report.setSuggestions(list);
        return driftReportRepository.save(report);
    }

    private static DriftSuggestion suggestion(Severity severity, SuggestionStatus status, boolean breaking) {
        DriftSuggestion s = new DriftSuggestion();
        s.setFilePath("README.md");
        s.setSeverity(severity);
        s.setStatus(status);
        s.setDriftType(DriftType.API_SIGNATURE_MISMATCH);
        s.setIsBreakingChange(breaking);
        s.setOldText("old");
        s.setSuggestedText("new");
        return s;
    }

    @Test
    void listsNewestReportsFirstInPages() throws Exception {
        mockMvc.perform(get("/api/reports?size=1").header("X-API-Key", "test-api-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].repositoryName").value("o/beta"))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void filtersByRepositorySeverityAndStatus() throws Exception {
        mockMvc.perform(get("/api/reports?repository=O/ALPHA").header("X-API-Key", "test-api-key"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].suggestions.length()").value(2));

        // Only reports with a matching suggestion, and only the matching suggestions inside them
        mockMvc.perform(get("/api/reports?severity=CRITICAL").header("X-API-Key", "test-api-key"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].suggestions.length()").value(1))
                .andExpect(jsonPath("$.content[0].suggestions[0].severity").value("CRITICAL"));

        mockMvc.perform(get("/api/reports?status=PENDING").header("X-API-Key", "test-api-key"))
                .andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get("/api/reports?status=REJECTED&severity=HIGH").header("X-API-Key", "test-api-key"))
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(get("/api/reports?severity=urgent").header("X-API-Key", "test-api-key"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void summaryCountsPendingSuggestions() throws Exception {
        mockMvc.perform(get("/api/reports/summary").header("X-API-Key", "test-api-key"))
                .andExpect(jsonPath("$.pending").value(2))
                .andExpect(jsonPath("$.critical").value(1))
                .andExpect(jsonPath("$.breaking").value(1));
    }

    @Test
    void approvingOpensPullRequestAndClearsDriftedStatus() throws Exception {
        when(gitHubService.createDocFixPullRequest(eq("o/alpha"),
                eq(List.of(new GitHubService.TextEdit("README.md", "old", "new"))), anyString(), anyString(), anyString()))
                .thenReturn(Mono.just("https://github.com/o/alpha/pull/1"));

        mockMvc.perform(post("/api/suggestions/" + alphaCritical.getId() + "/approve").header("X-API-Key", "test-api-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.prUrl").value("https://github.com/o/alpha/pull/1"));

        // That was alpha's last pending suggestion
        assertThat(repositoryConfigRepository.findFirstByNameIgnoreCase("o/alpha").orElseThrow().getStatus())
                .isEqualTo(RepositoryStatus.HEALTHY);

        mockMvc.perform(post("/api/suggestions/" + alphaCritical.getId() + "/approve").header("X-API-Key", "test-api-key"))
                .andExpect(status().isConflict());
    }

    @Test
    void approvalStaysPendingWhenTextNoLongerMatches() throws Exception {
        when(gitHubService.createDocFixPullRequest(anyString(), anyList(), anyString(), anyString(), anyString()))
                .thenReturn(Mono.error(new IllegalStateException("The outdated text was not found in README.md")));

        mockMvc.perform(post("/api/suggestions/" + alphaCritical.getId() + "/approve").header("X-API-Key", "test-api-key"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("The outdated text was not found in README.md"));
        assertThat(driftSuggestionRepository.findById(alphaCritical.getId()).orElseThrow().getStatus())
                .isEqualTo(SuggestionStatus.PENDING);
    }

    @Test
    void rejectingMarksSuggestionRejected() throws Exception {
        mockMvc.perform(post("/api/suggestions/" + alphaCritical.getId() + "/reject").header("X-API-Key", "test-api-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }
}
