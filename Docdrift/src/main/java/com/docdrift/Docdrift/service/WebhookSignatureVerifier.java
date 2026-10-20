package com.docdrift.Docdrift.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Verifies GitHub's X-Hub-Signature-256 header (HMAC-SHA256 of the raw request body).
 */
@Component
public class WebhookSignatureVerifier {

    private static final String PREFIX = "sha256=";

    private final byte[] secret;

    public WebhookSignatureVerifier(@Value("${github.webhook.secret}") String secret) {
        this.secret = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
    }

    public boolean isConfigured() {
        return secret.length > 0;
    }

    public boolean isValid(byte[] body, String signatureHeader) {
        if (!isConfigured() || signatureHeader == null || !signatureHeader.startsWith(PREFIX)) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] expected = HexFormat.of().formatHex(mac.doFinal(body)).getBytes(StandardCharsets.UTF_8);
            byte[] provided = signatureHeader.substring(PREFIX.length()).getBytes(StandardCharsets.UTF_8);
            return MessageDigest.isEqual(expected, provided);
        } catch (GeneralSecurityException e) {
            return false;
        }
    }
}
