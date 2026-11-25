package com.lystok;

import java.util.List;
import java.util.Map;

/**
 * Anything the API refused, or a request that never got an answer.
 *
 * <p>{@link #getCode()} is stable and meant for your code ({@code validation}, {@code not_found},
 * {@code rate_limited}, {@code plan_limit}, …); the message is for people. Without an answer from
 * the API, {@link #getStatus()} is 0 and the code is {@code timeout} or {@code connection_error}.
 */
public final class LystokException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /** One problem in the request body, such as {@code path = "to", message = "Invalid email"}. */
    public record Issue(String path, String message) {
    }

    private final int status;
    private final String code;
    private final transient Map<String, Object> params;
    private final transient List<Issue> issues;
    private final String requestId;
    private final Double retryAfter;

    LystokException(int status, String code, String message, Map<String, Object> params, List<Issue> issues,
                    String requestId, Double retryAfter, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.code = code;
        this.params = params == null ? Map.of() : params;
        this.issues = issues == null ? List.of() : issues;
        this.requestId = requestId;
        this.retryAfter = retryAfter;
    }

    LystokException(int status, String code, String message, Throwable cause) {
        this(status, code, message, null, null, null, null, cause);
    }

    /** The HTTP status, or 0 when no answer came. */
    public int getStatus() {
        return status;
    }

    /** {@code validation}, {@code not_found}, {@code plan_limit}, {@code access_ended}, {@code timeout}, {@code connection_error}, … */
    public String getCode() {
        return code;
    }

    /** Values behind the message, such as a limit. Empty when there are none. */
    public Map<String, Object> getParams() {
        return params;
    }

    /** Field problems of a validation error. Empty for other errors. */
    public List<Issue> getIssues() {
        return issues;
    }

    /** Quote it when you write to support; null when no answer came. */
    public String getRequestId() {
        return requestId;
    }

    /** Seconds the API asked to wait before trying again (429), or null. */
    public Double getRetryAfter() {
        return retryAfter;
    }
}
