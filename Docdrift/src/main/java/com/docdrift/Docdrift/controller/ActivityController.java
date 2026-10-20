package com.docdrift.Docdrift.controller;

import com.docdrift.Docdrift.dto.PageResponse;
import com.docdrift.Docdrift.model.WebhookEvent;
import com.docdrift.Docdrift.repository.WebhookEventRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** History of webhook deliveries and scans, newest first, for the dashboard's Activity tab. */
@RestController
@RequestMapping("/api/events")
public class ActivityController {

    private final WebhookEventRepository webhookEventRepository;

    public ActivityController(WebhookEventRepository webhookEventRepository) {
        this.webhookEventRepository = webhookEventRepository;
    }

    @GetMapping
    public PageResponse<WebhookEvent> getEvents(@RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size,
                                                @RequestParam(required = false) String repository) {
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100),
                Sort.by(Sort.Direction.DESC, "receivedAt").and(Sort.by(Sort.Direction.DESC, "id")));
        return PageResponse.from(repository == null || repository.isBlank()
                ? webhookEventRepository.findAll(pageRequest)
                : webhookEventRepository.findByRepositoryNameIgnoreCase(repository.trim(), pageRequest));
    }
}
