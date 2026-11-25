package com.lystok;

import java.time.Duration;

/**
 * Options for one call.
 *
 * @param idempotencyKey For sends: the same key within 24 hours gets the first answer back instead
 *     of a second email (use your order id, for example). Made up for you when null, which protects
 *     the call's own retries.
 * @param timeout How long one attempt of this call may take; the client's timeout when null.
 */
public record RequestOptions(String idempotencyKey, Duration timeout) {
    /** No options. */
    public static final RequestOptions NONE = new RequestOptions(null, null);

    /** Options with only an idempotency key. */
    public static RequestOptions withIdempotencyKey(String idempotencyKey) {
        return new RequestOptions(idempotencyKey, null);
    }

    /** Options with only a timeout. */
    public static RequestOptions withTimeout(Duration timeout) {
        return new RequestOptions(null, timeout);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String idempotencyKey;
        private Duration timeout;

        private Builder() {
        }

        public Builder idempotencyKey(String idempotencyKey) {
            this.idempotencyKey = idempotencyKey;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public RequestOptions build() {
            return new RequestOptions(idempotencyKey, timeout);
        }
    }
}
