package com.docdrift.Docdrift.service;

import com.docdrift.Docdrift.model.EventStatus;
import com.docdrift.Docdrift.model.RepositoryStatus;
import com.docdrift.Docdrift.repository.RepositoryConfigRepository;
import com.docdrift.Docdrift.repository.WebhookEventRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Analyses run in memory, so a restart loses any that were in progress.
 * On startup, mark them as failed so they do not show as running forever and can be rescanned.
 */
@Component
public class InterruptedScanRecovery {

    private static final String MESSAGE = "Interrupted by a server restart; run a manual scan to retry.";

    private final WebhookEventRepository webhookEventRepository;
    private final RepositoryConfigRepository repositoryConfigRepository;

    public InterruptedScanRecovery(WebhookEventRepository webhookEventRepository,
                                   RepositoryConfigRepository repositoryConfigRepository) {
        this.webhookEventRepository = webhookEventRepository;
        this.repositoryConfigRepository = repositoryConfigRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void markInterruptedScans() {
        webhookEventRepository.findByStatus(EventStatus.PENDING).forEach(event -> {
            event.setStatus(EventStatus.ERROR);
            event.setErrorMessage(MESSAGE);
            webhookEventRepository.save(event);
        });
        repositoryConfigRepository.findByStatus(RepositoryStatus.SCANNING).forEach(config -> {
            config.setStatus(RepositoryStatus.ERROR);
            config.setLastError(MESSAGE);
            repositoryConfigRepository.save(config);
        });
    }
}
