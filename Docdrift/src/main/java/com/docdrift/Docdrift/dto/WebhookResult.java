package com.docdrift.Docdrift.dto;

/** What happened to a webhook delivery. */
public record WebhookResult(Outcome outcome, String message) {

    public enum Outcome { STARTED, DUPLICATE, IGNORED }

    public static WebhookResult started() {
        return new WebhookResult(Outcome.STARTED, "Webhook received and processing started.");
    }

    public static WebhookResult duplicate() {
        return new WebhookResult(Outcome.DUPLICATE, "Duplicate delivery ignored.");
    }

    public static WebhookResult ignored(String reason) {
        return new WebhookResult(Outcome.IGNORED, "Ignored: " + reason);
    }
}
