package com.docdrift.Docdrift.controller;

import com.docdrift.Docdrift.dto.PullRequestPayload;
import com.docdrift.Docdrift.dto.WebhookPayload;
import com.docdrift.Docdrift.dto.WebhookResult;
import com.docdrift.Docdrift.repository.RepositoryConfigRepository;
import com.docdrift.Docdrift.service.DiffAnalyzerService;
import com.docdrift.Docdrift.service.WebhookSignatureVerifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/webhooks")
public class WebhookController {

    private final DiffAnalyzerService diffAnalyzerService;
    private final WebhookSignatureVerifier signatureVerifier;
    private final RepositoryConfigRepository repositoryConfigRepository;
    private final ObjectMapper objectMapper;

    public WebhookController(DiffAnalyzerService diffAnalyzerService,
                             WebhookSignatureVerifier signatureVerifier,
                             RepositoryConfigRepository repositoryConfigRepository,
                             ObjectMapper objectMapper) {
        this.diffAnalyzerService = diffAnalyzerService;
        this.signatureVerifier = signatureVerifier;
        this.repositoryConfigRepository = repositoryConfigRepository;
        this.objectMapper = objectMapper;
    }

    // Raw bytes are needed because the signature is computed over the exact request body
    @PostMapping("/github")
    public ResponseEntity<String> handleGitHubWebhook(
            @RequestBody byte[] body,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestHeader(value = "X-GitHub-Event", required = false) String eventType,
            @RequestHeader(value = "X-GitHub-Delivery", required = false) String deliveryId) {

        if (!signatureVerifier.isConfigured()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("Webhook secret is not configured on the server.");
        }
        if (!signatureVerifier.isValid(body, signature)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid webhook signature.");
        }

        if ("ping".equals(eventType)) {
            return ResponseEntity.ok("pong");
        }
        if (!"push".equals(eventType) && !"pull_request".equals(eventType)) {
            return ResponseEntity.accepted().body("Ignored event type: " + eventType);
        }

        WebhookPayload.RepositoryPayload repository;
        WebhookPayload push = null;
        PullRequestPayload pullRequest = null;
        try {
            if ("push".equals(eventType)) {
                push = objectMapper.readValue(body, WebhookPayload.class);
                repository = push.getRepository();
            } else {
                pullRequest = objectMapper.readValue(body, PullRequestPayload.class);
                repository = pullRequest.getRepository();
            }
        } catch (JacksonException e) {
            return ResponseEntity.badRequest().body("Malformed " + eventType + " payload.");
        }

        if (repository == null || repository.getFull_name() == null) {
            return ResponseEntity.badRequest().body("Payload has no repository.");
        }
        String repoName = repository.getFull_name();
        if (!repositoryConfigRepository.existsByNameIgnoreCase(repoName)) {
            return ResponseEntity.accepted().body("Repository is not tracked: " + repoName);
        }

        WebhookResult result = push != null
                ? diffAnalyzerService.processWebhookEvent(push, deliveryId)
                : diffAnalyzerService.processPullRequestEvent(pullRequest, deliveryId);
        return result.outcome() == WebhookResult.Outcome.IGNORED
                ? ResponseEntity.accepted().body(result.message())
                : ResponseEntity.ok(result.message());
    }
}
