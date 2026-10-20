package com.docdrift.Docdrift.service;

import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.util.retry.Retry;

import java.time.Duration;

/**
 * Retries calls that failed for temporary reasons: rate limits, server errors and network failures.
 */
public final class HttpRetry {

    private static final int MAX_ATTEMPTS = 3;

    private HttpRetry() {
    }

    public static Retry transientErrors(Duration minBackoff) {
        return Retry.backoff(MAX_ATTEMPTS, minBackoff)
                .maxBackoff(Duration.ofSeconds(30))
                .filter(HttpRetry::isTransient)
                .onRetryExhaustedThrow((spec, signal) -> signal.failure());
    }

    static boolean isTransient(Throwable error) {
        if (error instanceof WebClientRequestException) {
            return true;
        }
        if (error instanceof WebClientResponseException response) {
            int status = response.getStatusCode().value();
            return status == 429 || status >= 500;
        }
        return false;
    }
}
