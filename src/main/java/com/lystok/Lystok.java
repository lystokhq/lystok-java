package com.lystok;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The Lystok API client. Safe to share between threads; make one and keep it.
 *
 * <pre>{@code
 * Lystok lystok = new Lystok(System.getenv("LYSTOK_API_KEY"));
 * EmailCreated email = lystok.emails().send(SendEmailParams.builder()
 *         .from("Shop <orders@shop.example>")
 *         .to("olena@example.com")
 *         .subject("Your order #1042")
 *         .html("<p>Thank you!</p>")
 *         .build());
 * }</pre>
 */
public final class Lystok {
    /** This library's version. */
    public static final String VERSION = "0.1.0";

    private static final String DEFAULT_BASE_URL = "https://api.lystok.com";
    private static final Set<Integer> RETRY_STATUSES = Set.of(408, 429, 500, 502, 503, 504);

    private final String apiKey;
    private final String baseUrl;
    private final Duration timeout;
    private final int maxRetries;
    private final HttpClient http;
    private final Emails emails;
    private final Contacts contacts;
    private final Webhooks webhooks;

    /**
     * A client with this API key and the default options.
     *
     * @param apiKey an API key from Settings → For developers ({@code lk_live_…}); read it from
     *     wherever you keep secrets
     * @throws IllegalArgumentException when the key is null or blank
     */
    public Lystok(String apiKey) {
        this(builder().apiKey(apiKey));
    }

    private Lystok(Builder b) {
        if (b.apiKey == null || b.apiKey.isBlank()) {
            throw new IllegalArgumentException("Missing API key: pass your Lystok API key (lk_live_…) to new Lystok(apiKey) or Lystok.builder().apiKey(apiKey)");
        }
        this.apiKey = b.apiKey;
        this.baseUrl = (b.baseUrl != null ? b.baseUrl : DEFAULT_BASE_URL).replaceAll("/+$", "");
        this.timeout = b.timeout;
        this.maxRetries = Math.max(0, b.maxRetries);
        this.http = b.httpClient != null ? b.httpClient : HttpClient.newBuilder()
                .connectTimeout(b.timeout.compareTo(Duration.ofSeconds(10)) < 0 ? b.timeout : Duration.ofSeconds(10))
                .build();
        this.emails = new Emails(this);
        this.contacts = new Contacts(this);
        this.webhooks = new Webhooks();
    }

    public static Builder builder() {
        return new Builder();
    }

    public Emails emails() {
        return emails;
    }

    /** Needs a key with full access. */
    public Contacts contacts() {
        return contacts;
    }

    public Webhooks webhooks() {
        return webhooks;
    }

    <T> T request(String method, String path, Object body, String idempotencyKey, RequestOptions options, Class<T> type) {
        Duration wait = options != null && options.timeout() != null ? options.timeout() : timeout;
        HttpRequest.Builder rb = HttpRequest.newBuilder(URI.create(baseUrl + "/v1" + path))
                .timeout(wait)
                .header("Authorization", "Bearer " + apiKey)
                .header("Accept", "application/json")
                .header("User-Agent", "lystok-java/" + VERSION);
        if (body != null) {
            byte[] data;
            try {
                data = Json.MAPPER.writeValueAsBytes(body);
            } catch (IOException e) {
                throw new IllegalArgumentException("Could not turn the request into JSON", e);
            }
            rb.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofByteArray(data));
        } else {
            rb.method(method, HttpRequest.BodyPublishers.noBody());
        }
        if (idempotencyKey != null) {
            rb.header("Idempotency-Key", idempotencyKey);
        }
        HttpRequest request = rb.build();

        for (int attempt = 0; ; attempt++) {
            LystokException error;
            try {
                HttpResponse<byte[]> res = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
                int status = res.statusCode();
                if (status >= 200 && status < 300) {
                    return parse(res, type);
                }
                // A DELETE whose first try went through before the network failed finds nothing.
                if (status == 404 && method.equals("DELETE") && attempt > 0) {
                    return null;
                }
                error = errorFrom(res);
                boolean retry = RETRY_STATUSES.contains(status) || "idempotency_in_progress".equals(error.getCode());
                if (!retry || attempt >= maxRetries) {
                    throw error;
                }
            } catch (HttpTimeoutException e) {
                error = new LystokException(0, "timeout", "No answer from the Lystok API within " + wait.toMillis() + " ms", e);
                if (attempt >= maxRetries) {
                    throw error;
                }
            } catch (IOException e) {
                error = new LystokException(0, "connection_error", "Could not reach the Lystok API: " + e, e);
                if (attempt >= maxRetries) {
                    throw error;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new LystokException(0, "interrupted", "The call to the Lystok API was interrupted", e);
            }
            double backoff = Math.min(0.5 * Math.pow(2, attempt), 8.0) * (0.75 + ThreadLocalRandom.current().nextDouble() * 0.5);
            double seconds = error.getRetryAfter() != null ? Math.min(error.getRetryAfter(), 60.0) : backoff;
            try {
                Thread.sleep((long) (seconds * 1000));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new LystokException(0, "interrupted", "The call to the Lystok API was interrupted", e);
            }
        }
    }

    private static <T> T parse(HttpResponse<byte[]> res, Class<T> type) {
        if (type == Void.class || res.body().length == 0) {
            return null;
        }
        try {
            return Json.MAPPER.readValue(res.body(), type);
        } catch (IOException e) {
            throw new LystokException(res.statusCode(), "invalid_response", "The Lystok API answered with something that is not the expected JSON", e);
        }
    }

    private static LystokException errorFrom(HttpResponse<byte[]> res) {
        int status = res.statusCode();
        JsonNode e;
        try {
            e = Json.MAPPER.readTree(res.body()).path("error");
        } catch (IOException | RuntimeException ignored) {
            e = Json.MAPPER.missingNode();
        }
        Double retryAfter = null;
        String header = res.headers().firstValue("retry-after").orElse("");
        try {
            double value = Double.parseDouble(header);
            if (value > 0 && Double.isFinite(value)) {
                retryAfter = value;
            }
        } catch (NumberFormatException ignored) {
            // not a number of seconds
        }
        Map<String, Object> params = null;
        if (e.path("params").isObject()) {
            params = Json.MAPPER.convertValue(e.get("params"), Json.MAPPER.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
        }
        List<LystokException.Issue> issues = null;
        if (e.path("issues").isArray()) {
            issues = new ArrayList<>();
            for (JsonNode issue : e.get("issues")) {
                issues.add(new LystokException.Issue(issue.path("path").asText(), issue.path("message").asText()));
            }
        }
        return new LystokException(
                status,
                e.path("code").isTextual() ? e.get("code").asText() : (status >= 500 ? "server_error" : "http_error"),
                e.path("message").isTextual() ? e.get("message").asText() : "The Lystok API answered " + status,
                params,
                issues,
                e.path("requestId").isTextual() ? e.get("requestId").asText() : res.headers().firstValue("x-request-id").orElse(null),
                retryAfter,
                null);
    }

    static String segment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    static String newKey() {
        return UUID.randomUUID().toString();
    }

    public static final class Builder {
        private String apiKey;
        private String baseUrl;
        private Duration timeout = Duration.ofSeconds(30);
        private int maxRetries = 2;
        private HttpClient httpClient;

        private Builder() {
        }

        /** An API key from Settings → For developers ({@code lk_live_…}). Required. */
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /** Default {@code https://api.lystok.com}. */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /** How long one attempt may wait for the API. Default 30 seconds. */
        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * How many times to try again after a network error, a timeout, 429 or 5xx. Default 2.
         * Safe for emails too: every send carries an {@code Idempotency-Key}.
         */
        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        /** Your own {@link HttpClient}, for a proxy for example. */
        public Builder httpClient(HttpClient httpClient) {
            this.httpClient = httpClient;
            return this;
        }

        public Lystok build() {
            return new Lystok(this);
        }
    }
}
