package com.docdrift.Docdrift.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebhookSignatureVerifierTest {

    private static final byte[] BODY = "Hello, World!".getBytes(StandardCharsets.UTF_8);

    // Example from GitHub's "Validating webhook deliveries" documentation (secret "It's a Secret to Everybody")
    private static final String GITHUB_DOCS_SIGNATURE =
            "sha256=757107ea0eb2509fc211221cce984b8a37570b6d7586c22c46f4379c8b043e17";

    @Test
    void acceptsValidSignature() {
        WebhookSignatureVerifier verifier = new WebhookSignatureVerifier("It's a Secret to Everybody");
        assertTrue(verifier.isValid(BODY, GITHUB_DOCS_SIGNATURE));
    }

    @Test
    void rejectsWrongSecretMissingOrMalformedSignature() {
        WebhookSignatureVerifier verifier = new WebhookSignatureVerifier("another secret");
        assertFalse(verifier.isValid(BODY, GITHUB_DOCS_SIGNATURE));
        assertFalse(verifier.isValid(BODY, null));
        assertFalse(verifier.isValid(BODY, "757107ea0eb2509fc211221cce984b8a37570b6d7586c22c46f4379c8b043e17"));
    }

    @Test
    void rejectsEverythingWhenSecretIsNotConfigured() {
        WebhookSignatureVerifier verifier = new WebhookSignatureVerifier("");
        assertFalse(verifier.isConfigured());
        assertFalse(verifier.isValid(BODY, GITHUB_DOCS_SIGNATURE));
    }
}
