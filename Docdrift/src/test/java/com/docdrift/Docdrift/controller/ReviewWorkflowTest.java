package com.docdrift.Docdrift.controller;

import com.docdrift.Docdrift.model.DriftReport;
import com.docdrift.Docdrift.model.DriftSuggestion;
import com.docdrift.Docdrift.model.DriftType;
import com.docdrift.Docdrift.model.RepositoryConfig;
import com.docdrift.Docdrift.model.Severity;
import com.docdrift.Docdrift.model.SuggestionSource;
import com.docdrift.Docdrift.model.SuggestionStatus;
import com.docdrift.Docdrift.repository.DriftReportRepository;
import com.docdrift.Docdrift.repository.DriftSuggestionRepository;
import com.docdrift.Docdrift.repository.RepositoryConfigRepository;
import com.docdrift.Docdrift.repository.WebhookEventRepository;
import com.docdrift.Docdrift.service.GitHubService;
import com.docdrift.Docdrift.service.GitHubService.TextEdit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Edit-before-approve, batch approval, pull request commits and rule-based acknowledgements. */
@SpringBootTest
@AutoConfigureMockMvc
class ReviewWorkflowTest {

    private static final String KEY = "test-api-key";

    @Autowired private MockMvc mockMvc;
    @Autowired private RepositoryConfigRepository repositoryConfigRepository;
    @Autowired private DriftReportRepository driftReportRepository;
    @Autowired private DriftSuggestionRepository driftSuggestionRepository;
    @Autowired private WebhookEventRepository webhookEventRepository;

    @MockitoBean private GitHubService gitHubService;

    @BeforeEach
    void clean() {
        driftSuggestionRepository.deleteAll();
        driftReportRepository.deleteAll();
        webhookEventRepository.deleteAll();
        repositoryConfigRepository.deleteAll();
        RepositoryConfig config = new RepositoryConfig();
        config.setName("o/r");
        config.setUrl("https://github.com/o/r");
        repositoryConfigRepository.save(config);
    }

    private DriftReport report(String repo, Integer prNumber, String headBranch, DriftSuggestion... suggestions) {
        DriftReport report = new DriftReport();
        report.setRepositoryName(repo);
        report.setCommitSha("sha");
        report.setPullRequestNumber(prNumber);
        report.setHeadBranch(headBranch);
        List<DriftSuggestion> list = new ArrayList<>(List.of(suggestions));
        list.forEach(s -> s.setReport(report));
        report.setSuggestions(list);
        return driftReportRepository.save(report);
    }

    private static DriftSuggestion suggestion(String file, String oldText, String newText) {
        DriftSuggestion s = new DriftSuggestion();
        s.setFilePath(file);
        s.setOldText(oldText);
        s.setSuggestedText(newText);
        s.setSeverity(Severity.MEDIUM);
        s.setDriftType(DriftType.API_SIGNATURE_MISMATCH);
        return s;
    }

    private static String ids(DriftReport... reports) {
        List<String> ids = new ArrayList<>();
        for (DriftReport r : reports) r.getSuggestions().forEach(s -> ids.add(String.valueOf(s.getId())));
        return "{\"ids\":[" + String.join(",", ids) + "]}";
    }

    @Test
    void editingKeepsTheOriginalTextAndTheEditIsWhatGetsApplied() throws Exception {
        DriftSuggestion s = report("o/r", null, null, suggestion("README.md", "old", "ai text")).getSuggestions().getFirst();

        mockMvc.perform(patch("/api/suggestions/" + s.getId()).header("X-API-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"suggestedText\":\"reviewer text\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestedText").value("reviewer text"))
                .andExpect(jsonPath("$.originalSuggestedText").value("ai text"));

        when(gitHubService.createDocFixPullRequest(eq("o/r"), eq(List.of(new TextEdit("README.md", "old", "reviewer text"))),
                anyString(), anyString(), anyString())).thenReturn(Mono.just("https://github.com/o/r/pull/9"));
        mockMvc.perform(post("/api/suggestions/" + s.getId() + "/approve").header("X-API-Key", KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prUrl").value("https://github.com/o/r/pull/9"));

        mockMvc.perform(patch("/api/suggestions/" + s.getId()).header("X-API-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"suggestedText\":\"too late\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void batchApprovalPutsAllFixesInOnePullRequest() throws Exception {
        DriftReport first = report("o/r", null, null, suggestion("README.md", "a", "A"), suggestion("docs/api.md", "b", "B"));
        DriftReport second = report("o/r", null, null, suggestion("README.md", "c", "C"));
        when(gitHubService.createDocFixPullRequest(eq("o/r"), anyList(), anyString(), eq("docs: fix 3 documentation drift issues"),
                anyString())).thenReturn(Mono.just("https://github.com/o/r/pull/10"));

        mockMvc.perform(post("/api/suggestions/approve").header("X-API-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(ids(first, second)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prUrl").value("https://github.com/o/r/pull/10"))
                .andExpect(jsonPath("$.approved.length()").value(3));

        verify(gitHubService).createDocFixPullRequest(eq("o/r"), eq(List.of(
                new TextEdit("README.md", "a", "A"), new TextEdit("docs/api.md", "b", "B"), new TextEdit("README.md", "c", "C"))),
                anyString(), anyString(), anyString());
        assertThat(driftSuggestionRepository.findAll()).allMatch(s -> s.getStatus() == SuggestionStatus.APPROVED);
    }

    @Test
    void batchRejectsMixedRepositoriesOrPullRequests() throws Exception {
        DriftReport mine = report("o/r", null, null, suggestion("README.md", "a", "A"));
        DriftReport other = report("o/other", null, null, suggestion("README.md", "b", "B"));
        DriftReport pr = report("o/r", 5, "feature", suggestion("README.md", "c", "C"));

        mockMvc.perform(post("/api/suggestions/approve").header("X-API-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(ids(mine, other)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("All selected suggestions must belong to the same repository."));
        mockMvc.perform(post("/api/suggestions/approve").header("X-API-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(ids(mine, pr)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/suggestions/approve").header("X-API-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ids\":[]}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(gitHubService);
    }

    @Test
    void pullRequestSuggestionsAreCommittedToThePullRequestBranch() throws Exception {
        DriftSuggestion s = report("o/r", 5, "feature/x", suggestion("README.md", "a", "A")).getSuggestions().getFirst();
        when(gitHubService.commitDocFixes(eq("o/r"), eq("feature/x"), anyList(), anyString())).thenReturn(Mono.empty());

        mockMvc.perform(post("/api/suggestions/" + s.getId() + "/approve").header("X-API-Key", KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prUrl").value("https://github.com/o/r/pull/5"));
        verify(gitHubService).commitDocFixes(eq("o/r"), eq("feature/x"), eq(List.of(new TextEdit("README.md", "a", "A"))), anyString());
    }

    @Test
    void forkPullRequestCannotBeCommittedTo() throws Exception {
        DriftSuggestion s = report("o/r", 6, null, suggestion("README.md", "a", "A")).getSuggestions().getFirst();

        mockMvc.perform(post("/api/suggestions/" + s.getId() + "/approve").header("X-API-Key", KEY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("comes from a fork")));
        assertThat(driftSuggestionRepository.findById(s.getId()).orElseThrow().getStatus()).isEqualTo(SuggestionStatus.PENDING);
    }

    @Test
    void ruleBasedSuggestionIsAcknowledgedWithoutGitHub() throws Exception {
        DriftSuggestion rule = suggestion("openapi.yaml", null, "Remove the GET /x operation");
        rule.setSource(SuggestionSource.RULE);
        DriftSuggestion s = report("o/r", null, null, rule).getSuggestions().getFirst();

        mockMvc.perform(post("/api/suggestions/" + s.getId() + "/approve").header("X-API-Key", KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.prUrl").doesNotExist());
        verifyNoInteractions(gitHubService);
    }
}
