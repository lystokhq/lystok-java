package com.lystok;

import java.util.Map;

/**
 * What happened to an email. Times are ISO 8601 strings ({@code java.time.Instant.parse} reads
 * them), null until it happened.
 *
 * @param state {@code queued}, {@code sent}, {@code delivered}, {@code bounced}, {@code complained},
 *     {@code failed} or {@code suppressed}
 * @param error Why it failed or bounced, for people; null otherwise
 */
public record Email(
        String id,
        String from,
        String to,
        String subject,
        String templateId,
        String state,
        String error,
        Map<String, String> tags,
        String createdAt,
        String sentAt,
        String deliveredAt,
        String bouncedAt,
        String complainedAt,
        String openedAt,
        String clickedAt) {
}
