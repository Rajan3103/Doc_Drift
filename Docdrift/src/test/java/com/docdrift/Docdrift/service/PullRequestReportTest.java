package com.docdrift.Docdrift.service;

import com.docdrift.Docdrift.model.DriftSuggestion;
import com.docdrift.Docdrift.model.Severity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PullRequestReportTest {

    private static DriftSuggestion suggestion(Severity severity, boolean breaking) {
        DriftSuggestion s = new DriftSuggestion();
        s.setSeverity(severity);
        s.setIsBreakingChange(breaking);
        s.setFilePath("README.md");
        s.setImpactedSymbol("GET /api/orders");
        s.setReason("Route | renamed");
        return s;
    }

    @Test
    void cleanPullRequestPassesWithMarker() {
        assertThat(PullRequestReport.statusState(List.of())).isEqualTo("success");
        assertThat(PullRequestReport.comment("abcdef1234", List.of(), ""))
                .startsWith(PullRequestReport.MARKER)
                .contains("No documentation drift found for `abcdef1`");
    }

    @Test
    void breakingDriftFailsStatusAndIsListedInComment() {
        List<DriftSuggestion> suggestions = List.of(suggestion(Severity.LOW, false), suggestion(Severity.HIGH, true));

        assertThat(PullRequestReport.statusState(suggestions)).isEqualTo("failure");
        assertThat(PullRequestReport.statusDescription(suggestions)).isEqualTo("2 documentation update(s) suggested, 1 for breaking/critical changes");
        String comment = PullRequestReport.comment("abc", suggestions, "https://docdrift.example.com");
        assertThat(comment).contains("Found **2**").contains("Route \\| renamed").contains("[DocDrift dashboard](https://docdrift.example.com)");
        // Highest severity first
        assertThat(comment.indexOf("| HIGH")).isLessThan(comment.indexOf("| LOW"));
    }

    @Test
    void nonBreakingDriftPasses() {
        assertThat(PullRequestReport.statusState(List.of(suggestion(Severity.MEDIUM, false)))).isEqualTo("success");
    }
}
