package com.lystok;

import java.util.List;

/** Sending emails and reading what happened to them. */
public final class Emails {
    private final Lystok client;

    Emails(Lystok client) {
        this.client = client;
    }

    /** Sends one email. It is queued and usually sent within a second. */
    public EmailCreated send(SendEmailParams email) {
        return send(email, RequestOptions.NONE);
    }

    /**
     * Sends one email. It is queued and usually sent within a second.
     *
     * @param options {@link RequestOptions#withIdempotencyKey(String)} sends it only once per key
     *     within 24 hours
     */
    public EmailCreated send(SendEmailParams email, RequestOptions options) {
        return client.request("POST", "/emails", email, keyOf(options), options, EmailCreated.class);
    }

    /** Sends up to 100 emails in one request; all are accepted or none. Ids come in the same order. */
    public EmailBatchCreated sendBatch(List<SendEmailParams> emails) {
        return sendBatch(emails, RequestOptions.NONE);
    }

    /** Sends up to 100 emails in one request; all are accepted or none. Ids come in the same order. */
    public EmailBatchCreated sendBatch(List<SendEmailParams> emails, RequestOptions options) {
        return client.request("POST", "/emails/batch", emails, keyOf(options), options, EmailBatchCreated.class);
    }

    /** What happened to an email. */
    public Email get(String id) {
        return get(id, RequestOptions.NONE);
    }

    /** What happened to an email. */
    public Email get(String id, RequestOptions options) {
        return client.request("GET", "/emails/" + Lystok.segment(id), null, null, options, Email.class);
    }

    private static String keyOf(RequestOptions options) {
        return options != null && options.idempotencyKey() != null ? options.idempotencyKey() : Lystok.newKey();
    }
}
