package com.lystok;

/** Your contacts. Needs a key with full access. */
public final class Contacts {
    private final Lystok client;

    Contacts(Lystok client) {
        this.client = client;
    }

    /** Adds a person, or updates them when the address is already there. Safe to repeat. */
    public Contact upsert(UpsertContactParams contact) {
        return upsert(contact, RequestOptions.NONE);
    }

    /** Adds a person, or updates them when the address is already there. Safe to repeat. */
    public Contact upsert(UpsertContactParams contact, RequestOptions options) {
        return client.request("POST", "/contacts", contact, null, options, Contact.class);
    }

    /** A person, by id or by email address. */
    public Contact get(String idOrEmail) {
        return get(idOrEmail, RequestOptions.NONE);
    }

    /** A person, by id or by email address. */
    public Contact get(String idOrEmail, RequestOptions options) {
        return client.request("GET", "/contacts/" + Lystok.segment(idOrEmail), null, null, options, Contact.class);
    }

    /** Changes a person, found by id or by email address. */
    public Contact update(String idOrEmail, UpdateContactParams changes) {
        return update(idOrEmail, changes, RequestOptions.NONE);
    }

    /** Changes a person, found by id or by email address. */
    public Contact update(String idOrEmail, UpdateContactParams changes, RequestOptions options) {
        return client.request("PATCH", "/contacts/" + Lystok.segment(idOrEmail), changes, null, options, Contact.class);
    }

    /** Deletes a person, found by id or by email address. */
    public void delete(String idOrEmail) {
        delete(idOrEmail, RequestOptions.NONE);
    }

    /** Deletes a person, found by id or by email address. */
    public void delete(String idOrEmail, RequestOptions options) {
        client.request("DELETE", "/contacts/" + Lystok.segment(idOrEmail), null, null, options, Void.class);
    }
}
