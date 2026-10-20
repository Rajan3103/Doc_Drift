package com.docdrift.Docdrift.service;

import com.docdrift.Docdrift.model.DriftSuggestion;
import com.docdrift.Docdrift.model.RepositoryConfig;
import com.docdrift.Docdrift.model.Severity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AlertServiceTest {

    private final List<ClientRequest> slackRequests = new ArrayList<>();
    private final JavaMailSender mailSender = mock(JavaMailSender.class);

    @SuppressWarnings("unchecked")
    private AlertService service(HttpStatus slackStatus) {
        WebClient.Builder builder = WebClient.builder().exchangeFunction(request -> {
            slackRequests.add(request);
            return Mono.just(ClientResponse.create(slackStatus).build());
        });
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mailSender);
        return new AlertService(builder, provider, "docdrift@example.com", "https://dash.example.com");
    }

    private static RepositoryConfig config() {
        RepositoryConfig config = new RepositoryConfig();
        config.setName("o/r");
        config.setSlackWebhookUrl("https://hooks.slack.com/services/T/B/X");
        config.setAlertEmail("team@example.com");
        return config;
    }

    private static DriftSuggestion suggestion(Severity severity, boolean breaking) {
        DriftSuggestion s = new DriftSuggestion();
        s.setSeverity(severity);
        s.setIsBreakingChange(breaking);
        s.setFilePath("README.md");
        s.setImpactedSymbol("GET /api/orders");
        return s;
    }

    @Test
    void alertsSlackAndEmailForBreakingOrCriticalDrift() {
        List<String> warnings = service(HttpStatus.OK).alertIfNeeded(config(), "abcdef123", 7,
                List.of(suggestion(Severity.CRITICAL, false), suggestion(Severity.LOW, false)));

        assertThat(warnings).isEmpty();
        assertThat(slackRequests).hasSize(1);
        assertThat(slackRequests.getFirst().url().toString()).isEqualTo("https://hooks.slack.com/services/T/B/X");
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void noAlertForMinorDrift() {
        service(HttpStatus.OK).alertIfNeeded(config(), "abc", null, List.of(suggestion(Severity.MEDIUM, false)));
        assertThat(slackRequests).isEmpty();
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void deliveryFailuresBecomeWarnings() {
        List<String> warnings = service(HttpStatus.NOT_FOUND).alertIfNeeded(config(), "abc", null,
                List.of(suggestion(Severity.HIGH, true)));
        assertThat(warnings).anyMatch(w -> w.startsWith("Slack alert failed"));
    }

    @Test
    void messageNamesRepositoryPullRequestAndDashboard() {
        String message = service(HttpStatus.OK).message("o/r", "abcdef123", 7, List.of(suggestion(Severity.CRITICAL, true)));
        assertThat(message).contains("o/r (pull request #7) at abcdef1").contains("[CRITICAL] README.md - GET /api/orders")
                .contains("https://dash.example.com");
    }
}
