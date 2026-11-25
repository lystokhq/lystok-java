package com.lystok;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Checking that a webhook came from Lystok (the Standard Webhooks scheme): the headers
 * {@code webhook-id}, {@code webhook-timestamp} and {@code webhook-signature} sign
 * {@code id.timestamp.body} with HMAC-SHA256.
 */
public final class Webhook {
    /** How far the timestamp may be from now by default: 5 minutes. */
    public static final Duration DEFAULT_TOLERANCE = Duration.ofMinutes(5);
    private static final String PREFIX = "whsec_";

    private Webhook() {
    }

    /**
     * Checks a webhook and returns its event.
     *
     * @param payload The <b>raw body</b>, exactly as received, before any JSON parsing
     * @param headers The request's headers in any case; values may be strings or lists of strings
     *     (Spring's {@code @RequestHeader Map<String, String>}, Ktor's {@code headers.toMap()})
     * @param secret The webhook's signing secret ({@code whsec_...}, from its menu → Signing secret)
     * @throws WebhookVerificationException When the signature or timestamp is wrong
     */
    public static WebhookEvent verify(String payload, Map<String, ?> headers, String secret) {
        return verify(payload.getBytes(StandardCharsets.UTF_8), headers, secret, DEFAULT_TOLERANCE, Instant.now());
    }

    /** {@link #verify(String, Map, String)} for a body read as bytes. */
    public static WebhookEvent verify(byte[] payload, Map<String, ?> headers, String secret) {
        return verify(payload, headers, secret, DEFAULT_TOLERANCE, Instant.now());
    }

    /**
     * {@link #verify(String, Map, String)} with your own tolerance and clock.
     *
     * @param tolerance How far the timestamp may be from {@code now}
     * @param now The current time; for tests
     */
    public static WebhookEvent verify(String payload, Map<String, ?> headers, String secret, Duration tolerance, Instant now) {
        return verify(payload.getBytes(StandardCharsets.UTF_8), headers, secret, tolerance, now);
    }

    /** {@link #verify(String, Map, String, Duration, Instant)} for a body read as bytes. */
    public static WebhookEvent verify(byte[] payload, Map<String, ?> headers, String secret, Duration tolerance, Instant now) {
        String id = header(headers, "webhook-id");
        String timestamp = header(headers, "webhook-timestamp");
        String signatures = header(headers, "webhook-signature");
        if (id == null || id.isEmpty() || timestamp == null || timestamp.isEmpty() || signatures == null || signatures.isEmpty()) {
            throw new WebhookVerificationException("Missing webhook-id, webhook-timestamp or webhook-signature header");
        }
        long sent;
        try {
            if (!timestamp.chars().allMatch(c -> c >= '0' && c <= '9')) {
                throw new NumberFormatException();
            }
            sent = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            throw new WebhookVerificationException("Invalid webhook-timestamp header");
        }
        if (Math.abs(now.getEpochSecond() - sent) > tolerance.getSeconds()) {
            throw new WebhookVerificationException("Webhook timestamp is too far from now");
        }

        byte[] key;
        try {
            key = Base64.getDecoder().decode(secret.startsWith(PREFIX) ? secret.substring(PREFIX.length()) : secret);
        } catch (IllegalArgumentException e) {
            throw new WebhookVerificationException("Invalid webhook secret");
        }
        if (key.length == 0) {
            throw new WebhookVerificationException("Invalid webhook secret");
        }

        byte[] expected;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            mac.update((id + "." + timestamp + ".").getBytes(StandardCharsets.UTF_8));
            expected = Base64.getEncoder().encode(mac.doFinal(payload));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HmacSHA256 is not available", e);
        }

        for (String entry : signatures.split(" ")) {
            int comma = entry.indexOf(',');
            if (comma < 0 || !entry.substring(0, comma).equals("v1") || comma == entry.length() - 1) {
                continue;
            }
            if (MessageDigest.isEqual(expected, entry.substring(comma + 1).getBytes(StandardCharsets.UTF_8))) {
                try {
                    return Json.MAPPER.readValue(payload, WebhookEvent.class);
                } catch (IOException e) {
                    throw new WebhookVerificationException("The webhook body is not a JSON object");
                }
            }
        }
        throw new WebhookVerificationException("No matching webhook signature");
    }

    private static String header(Map<String, ?> headers, String name) {
        for (Map.Entry<String, ?> entry : headers.entrySet()) {
            if (entry.getKey() == null || !entry.getKey().equalsIgnoreCase(name)) {
                continue;
            }
            Object value = entry.getValue();
            if (value == null) {
                return null;
            }
            if (value instanceof Iterable<?> values) {
                List<String> parts = new ArrayList<>();
                values.forEach(v -> parts.add(String.valueOf(v)));
                return String.join(" ", parts);
            }
            if (value instanceof String[] values) {
                return String.join(" ", values);
            }
            return value.toString();
        }
        return null;
    }
}
