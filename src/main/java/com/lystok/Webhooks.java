package com.lystok;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/** Checking webhooks; the same as {@link Webhook}, reachable from the client. */
public final class Webhooks {
    Webhooks() {
    }

    /** See {@link Webhook#verify(String, Map, String)}. */
    public WebhookEvent verify(String payload, Map<String, ?> headers, String secret) {
        return Webhook.verify(payload, headers, secret);
    }

    /** See {@link Webhook#verify(String, Map, String, Duration, Instant)}. */
    public WebhookEvent verify(String payload, Map<String, ?> headers, String secret, Duration tolerance, Instant now) {
        return Webhook.verify(payload, headers, secret, tolerance, now);
    }
}
