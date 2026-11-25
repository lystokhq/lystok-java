package com.lystok;

import java.util.Map;

/**
 * An event from a webhook.
 *
 * @param type {@code email.sent}, {@code email.delivered}, {@code email.bounced},
 *     {@code email.complained}, {@code email.failed}, {@code email.suppressed},
 *     {@code email.opened}, {@code email.clicked} or {@code contact.unsubscribed}
 * @param createdAt When it happened, ISO 8601
 * @param data What it is about, such as {@code email_id}, {@code to} and {@code tags}
 */
public record WebhookEvent(String type, String createdAt, Map<String, Object> data) {
}
