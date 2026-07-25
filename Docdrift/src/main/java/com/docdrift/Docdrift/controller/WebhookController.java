package com.docdrift.Docdrift.controller;

import com.docdrift.Docdrift.dto.WebhookPayload;
import com.docdrift.Docdrift.service.DiffAnalyzerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/webhooks")
public class WebhookController {

    private final DiffAnalyzerService diffAnalyzerService;

    public WebhookController(DiffAnalyzerService diffAnalyzerService) {
        this.diffAnalyzerService = diffAnalyzerService;
    }

    @PostMapping("/github")
    public ResponseEntity<String> handleGitHubWebhook(@RequestBody WebhookPayload payload) {
        diffAnalyzerService.processWebhookEvent(payload);
        return ResponseEntity.ok("Webhook received and processing started.");
    }
}
