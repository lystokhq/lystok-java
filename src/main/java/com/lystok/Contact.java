package com.lystok;

import java.util.List;
import java.util.Map;

/**
 * A person in your contacts. Times are ISO 8601 strings.
 *
 * @param status {@code subscribed}, {@code unsubscribed}, {@code bounced} or {@code complained}
 * @param fields Your own fields by key: strings, numbers or booleans
 * @param source Where the contact came from, such as {@code api} or {@code import}
 */
public record Contact(
        String id,
        String email,
        String firstName,
        String lastName,
        String phone,
        String locale,
        String status,
        Map<String, Object> fields,
        List<String> lists,
        List<String> tags,
        String source,
        String consentSource,
        String consentAt,
        String createdAt,
        String updatedAt) {
}
