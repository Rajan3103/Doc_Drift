package com.docdrift.Docdrift.service;

import com.docdrift.Docdrift.model.DriftSuggestion;
import com.docdrift.Docdrift.model.Severity;

import java.util.Comparator;
import java.util.List;

/** Builds the comment and commit status DocDrift posts on a pull request. */
public final class PullRequestReport {

    /** Hidden marker that identifies DocDrift's comment, so later pushes update it instead of adding new ones. */
    public static final String MARKER = "<!-- docdrift-report -->";
    public static final String STATUS_CONTEXT = "docdrift/docs";

    private static final int MAX_LISTED = 10;

    private PullRequestReport() {
    }

    public static boolean isBlocking(List<DriftSuggestion> suggestions) {
        return suggestions.stream().anyMatch(s -> Boolean.TRUE.equals(s.getIsBreakingChange()) || s.getSeverity() == Severity.CRITICAL);
    }

    /** failure when a breaking or critical change needs documentation updates, otherwise success. */
    public static String statusState(List<DriftSuggestion> suggestions) {
        return isBlocking(suggestions) ? "failure" : "success";
    }

    public static String statusDescription(List<DriftSuggestion> suggestions) {
        if (suggestions.isEmpty()) {
            return "Documentation is in sync with this change";
        }
        long blocking = suggestions.stream()
                .filter(s -> Boolean.TRUE.equals(s.getIsBreakingChange()) || s.getSeverity() == Severity.CRITICAL).count();
        return suggestions.size() + " documentation update(s) suggested"
                + (blocking > 0 ? ", " + blocking + " for breaking/critical changes" : "");
    }

    public static String comment(String headSha, List<DriftSuggestion> suggestions, String dashboardUrl) {
        StringBuilder body = new StringBuilder(MARKER).append("\n### 📄 DocDrift documentation check\n\n");
        String shortSha = headSha.length() > 7 ? headSha.substring(0, 7) : headSha;

        if (suggestions.isEmpty()) {
            body.append("✅ No documentation drift found for `").append(shortSha).append("`.\n");
        } else {
            body.append("Found **").append(suggestions.size()).append("** documentation update(s) needed for `")
                    .append(shortSha).append("`:\n\n")
                    .append("| Severity | File | What changed | Breaking |\n|---|---|---|---|\n");
            suggestions.stream()
                    .sorted(Comparator.comparing(DriftSuggestion::getSeverity))
                    .limit(MAX_LISTED)
                    .forEach(s -> body.append("| ").append(s.getSeverity())
                            .append(" | `").append(cell(s.getFilePath())).append("` | ")
                            .append(cell(s.getImpactedSymbol())).append(": ").append(cell(truncate(s.getReason(), 160)))
                            .append(" | ").append(Boolean.TRUE.equals(s.getIsBreakingChange()) ? "⚠️ yes" : "no").append(" |\n"));
            if (suggestions.size() > MAX_LISTED) {
                body.append("\n…and ").append(suggestions.size() - MAX_LISTED).append(" more.\n");
            }
        }
        if (dashboardUrl != null && !dashboardUrl.isBlank()) {
            body.append("\nReview and apply the suggested fixes in the [DocDrift dashboard](").append(dashboardUrl).append(").\n");
        }
        return body.toString();
    }

    private static String cell(String text) {
        return text == null ? "" : text.replace("|", "\\|").replace("\n", " ");
    }

    private static String truncate(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }
}
