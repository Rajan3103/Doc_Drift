package com.docdrift.Docdrift.controller;

import com.docdrift.Docdrift.model.DriftReport;
import com.docdrift.Docdrift.model.DriftSuggestion;
import com.docdrift.Docdrift.model.DriftType;
import com.docdrift.Docdrift.model.EventStatus;
import com.docdrift.Docdrift.model.RepositoryConfig;
import com.docdrift.Docdrift.model.Severity;
import com.docdrift.Docdrift.model.WebhookEvent;
import com.docdrift.Docdrift.repository.DriftReportRepository;
import com.docdrift.Docdrift.repository.DriftSuggestionRepository;
import com.docdrift.Docdrift.repository.RepositoryConfigRepository;
import com.docdrift.Docdrift.repository.WebhookEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SettingsAndActivityTest {

    private static final String KEY = "test-api-key";
    private static final String VALID = """
            {"docPaths":["README.md","docs/"],"watchedBranches":["main","release/*"],"minSeverity":"HIGH",
             "minConfidence":0.7,"analyzePullRequests":false,
             "slackWebhookUrl":"https://hooks.slack.com/services/T00/B00/XYZ","alertEmail":"team@example.com"}""";

    @Autowired private MockMvc mockMvc;
    @Autowired private RepositoryConfigRepository repositoryConfigRepository;
    @Autowired private DriftReportRepository driftReportRepository;
    @Autowired private DriftSuggestionRepository driftSuggestionRepository;
    @Autowired private WebhookEventRepository webhookEventRepository;

    private Long repoId;

    @BeforeEach
    void setUp() {
        driftSuggestionRepository.deleteAll();
        driftReportRepository.deleteAll();
        webhookEventRepository.deleteAll();
        repositoryConfigRepository.deleteAll();
        RepositoryConfig config = new RepositoryConfig();
        config.setName("o/r");
        config.setUrl("https://github.com/o/r");
        repoId = repositoryConfigRepository.save(config).getId();
    }

    private ResultActions putSettings(String json) throws Exception {
        return mockMvc.perform(put("/api/repos/" + repoId + "/settings").header("X-API-Key", KEY)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void savesSettingsWithoutExposingTheSlackWebhook() throws Exception {
        putSettings(VALID)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.docPaths[1]").value("docs/"))
                .andExpect(jsonPath("$.watchedBranches[1]").value("release/*"))
                .andExpect(jsonPath("$.minSeverity").value("HIGH"))
                .andExpect(jsonPath("$.analyzePullRequests").value(false))
                .andExpect(jsonPath("$.slackAlertsConfigured").value(true))
                .andExpect(jsonPath("$.slackWebhookUrl").doesNotExist())
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("hooks.slack.com"))));

        RepositoryConfig saved = repositoryConfigRepository.findById(repoId).orElseThrow();
        assertThat(saved.getSlackWebhookUrl()).isEqualTo("https://hooks.slack.com/services/T00/B00/XYZ");

        // null keeps the webhook, "" removes it
        putSettings(VALID.replace("\"https://hooks.slack.com/services/T00/B00/XYZ\"", "null")).andExpect(status().isOk());
        assertThat(repositoryConfigRepository.findById(repoId).orElseThrow().isSlackAlertsConfigured()).isTrue();
        putSettings(VALID.replace("https://hooks.slack.com/services/T00/B00/XYZ", "")).andExpect(status().isOk());
        assertThat(repositoryConfigRepository.findById(repoId).orElseThrow().isSlackAlertsConfigured()).isFalse();
    }

    @Test
    void rejectsUnsafeOrInvalidSettings() throws Exception {
        putSettings(VALID.replace("https://hooks.slack.com/services/T00/B00/XYZ", "http://169.254.169.254/latest"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("slackWebhookUrl")));
        putSettings(VALID.replace("\"docs/\"", "\"../secrets\"")).andExpect(status().isBadRequest());
        putSettings(VALID.replace("\"docs/\"", "\"/etc/passwd\"")).andExpect(status().isBadRequest());
        putSettings(VALID.replace("0.7", "1.5")).andExpect(status().isBadRequest());
        putSettings(VALID.replace("team@example.com", "not-an-email")).andExpect(status().isBadRequest());
        putSettings(VALID.replace("\"release/*\"", "\"bad branch\"")).andExpect(status().isBadRequest());
    }

    @Test
    void historyCountsSuggestionsPerDayBySeverity() throws Exception {
        DriftReport report = new DriftReport();
        report.setRepositoryName("o/r");
        report.setAnalyzedAt(LocalDateTime.now());
        List<DriftSuggestion> suggestions = new ArrayList<>();
        for (Severity severity : List.of(Severity.CRITICAL, Severity.LOW, Severity.LOW)) {
            DriftSuggestion s = new DriftSuggestion();
            s.setSeverity(severity);
            s.setDriftType(DriftType.SCHEMA_DRIFT);
            s.setReport(report);
            suggestions.add(s);
        }
        report.setSuggestions(suggestions);
        driftReportRepository.save(report);

        mockMvc.perform(get("/api/repos/" + repoId + "/history?days=7").header("X-API-Key", KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(7))
                .andExpect(jsonPath("$[6].date").value(LocalDate.now().toString()))
                .andExpect(jsonPath("$[6].critical").value(1))
                .andExpect(jsonPath("$[6].low").value(2))
                .andExpect(jsonPath("$[0].critical").value(0));
    }

    @Test
    void activityListsEventsNewestFirstAndFiltersByRepository() throws Exception {
        for (String repo : List.of("o/r", "o/other", "o/r")) {
            WebhookEvent event = new WebhookEvent();
            event.setRepositoryName(repo);
            event.setEventType("push");
            event.setStatus(EventStatus.PROCESSED);
            webhookEventRepository.save(event);
        }

        mockMvc.perform(get("/api/events?size=2").header("X-API-Key", KEY))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content.length()").value(2));
        mockMvc.perform(get("/api/events?repository=O/R").header("X-API-Key", KEY))
                .andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get("/api/events")).andExpect(status().isUnauthorized());
    }
}
