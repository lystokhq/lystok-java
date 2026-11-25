package com.lystok;

import java.util.List;

/**
 * Queued emails.
 *
 * @param data Their ids, in the same order as sent
 */
public record EmailBatchCreated(List<EmailCreated> data) {
}
