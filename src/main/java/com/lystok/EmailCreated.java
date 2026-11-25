package com.lystok;

/**
 * A queued email.
 *
 * @param id Its id, for {@link Emails#get(String)}
 */
public record EmailCreated(String id) {
}
