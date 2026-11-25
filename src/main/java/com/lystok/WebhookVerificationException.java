package com.lystok;

/** A webhook whose signature or timestamp is wrong: answer it with 401 and do not trust it. */
public final class WebhookVerificationException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    WebhookVerificationException(String message) {
        super(message);
    }
}
