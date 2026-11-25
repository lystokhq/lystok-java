package com.lystok;

/** Whether to count opens and clicks of an email; the account's setting when null. */
public record Tracking(Boolean opens, Boolean clicks) {
}
