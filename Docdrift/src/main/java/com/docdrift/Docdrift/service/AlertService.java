package com.docdrift.Docdrift.service;

import com.docdrift.Docdrift.model.DriftSuggestion;
import com.docdrift.Docdrift.model.RepositoryConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Sends Slack and email alerts when an analysis finds breaking or critical drift.
 * Alerts are best-effort: failures are returned as warnings and never fail the analysis.
 */
@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private final WebClient webClient;
    private final ObjectProvider<JavaMailSender> mailSender;
    private final String mailFrom;
    private final String dashboardUrl;

    public AlertService(WebClient.Builder webClientBuilder,
                        ObjectProvider<JavaMailSender> mailSender,
                        @Value("${docdrift.alerts.email-from:docdrift@localhost}") String mailFrom,
                        @Value("${docdrift.dashboard-url:}") String dashboardUrl) {
        this.webClient = webClientBuilder.clone().build();
        this.mailSender = mailSender;
        this.mailFrom = mailFrom;
        this.dashboardUrl = dashboardUrl;
    }

    /** Returns warnings for alerts that could not be delivered. */
    public List<String> alertIfNeeded(RepositoryConfig config, String commitSha, Integer pullRequestNumber,
                                      List<DriftSuggestion> suggestions) {
        List<DriftSuggestion> alertable = suggestions.stream()
                .filter(s -> Boolean.TRUE.equals(s.getIsBreakingChange())
                        || s.getSeverity() == com.docdrift.Docdrift.model.Severity.CRITICAL)
                .toList();
        List<String> warnings = new ArrayList<>();
        if (alertable.isEmpty()) {
            return warnings;
        }
        String text = message(config.getName(), commitSha, pullRequestNumber, alertable);

        if (config.isSlackAlertsConfigured()) {
            try {
                webClient.post().uri(config.getSlackWebhookUrl())
                        .bodyValue(Map.of("text", text))
                        .retrieve().toBodilessEntity()
                        .block(Duration.ofSeconds(15));
            } catch (RuntimeException e) {
                log.warn("Slack alert for {} failed", config.getName(), e);
                warnings.add("Slack alert failed: " + e.getMessage());
            }
        }

        if (config.getAlertEmail() != null && !config.getAlertEmail().isBlank()) {
            JavaMailSender sender = mailSender.getIfAvailable();
            if (sender == null) {
                warnings.add("Email alert skipped: no mail server configured (set SPRING_MAIL_HOST).");
            } else {
                try {
                    SimpleMailMessage mail = new SimpleMailMessage();
                    mail.setFrom(mailFrom);
                    mail.setTo(config.getAlertEmail());
                    mail.setSubject("DocDrift: " + alertable.size() + " breaking/critical doc change(s) in " + config.getName());
                    mail.setText(text);
                    sender.send(mail);
                } catch (RuntimeException e) {
                    log.warn("Email alert for {} failed", config.getName(), e);
                    warnings.add("Email alert failed: " + e.getMessage());
                }
            }
        }
        return warnings;
    }

    String message(String repoName, String commitSha, Integer pullRequestNumber, List<DriftSuggestion> alertable) {
        String shortSha = commitSha != null && commitSha.length() > 7 ? commitSha.substring(0, 7) : commitSha;
        StringBuilder text = new StringBuilder("🚨 DocDrift found ").append(alertable.size())
                .append(" breaking/critical documentation issue(s) in ").append(repoName)
                .append(pullRequestNumber != null ? " (pull request #" + pullRequestNumber + ")" : "")
                .append(" at ").append(shortSha).append(":\n");
        alertable.stream().limit(10).forEach(s -> text.append("• [").append(s.getSeverity()).append("] ")
                .append(s.getFilePath()).append(" - ").append(s.getImpactedSymbol()).append('\n'));
        if (dashboardUrl != null && !dashboardUrl.isBlank()) {
            text.append("Review: ").append(dashboardUrl);
        }
        return text.toString();
    }
}
