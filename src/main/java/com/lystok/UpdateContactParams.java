package com.lystok;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Changes to a person; what is left out stays as it is. Make one with {@link #builder()}.
 *
 * @param fields Your own fields by key; null removes a value
 * @param lists Lists to add the person to
 * @param tags Tags to add
 * @param status {@code unsubscribed} unsubscribes the person from your newsletters
 */
public record UpdateContactParams(
        String email,
        String firstName,
        String lastName,
        String phone,
        String locale,
        Map<String, Object> fields,
        List<String> lists,
        List<String> tags,
        String consentSource,
        List<String> removeLists,
        List<String> removeTags,
        String status) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String email;
        private String firstName;
        private String lastName;
        private String phone;
        private String locale;
        private Map<String, Object> fields;
        private List<String> lists;
        private List<String> tags;
        private String consentSource;
        private List<String> removeLists;
        private List<String> removeTags;
        private String status;

        private Builder() {
        }

        /** A new address for the person. */
        public Builder email(String email) {
            this.email = email;
            return this;
        }

        public Builder firstName(String firstName) {
            this.firstName = firstName;
            return this;
        }

        public Builder lastName(String lastName) {
            this.lastName = lastName;
            return this;
        }

        public Builder phone(String phone) {
            this.phone = phone;
            return this;
        }

        public Builder locale(String locale) {
            this.locale = locale;
            return this;
        }

        /** One of your own fields; null removes its value. */
        public Builder field(String key, Object value) {
            if (this.fields == null) {
                this.fields = new LinkedHashMap<>();
            }
            this.fields.put(key, value);
            return this;
        }

        public Builder lists(List<String> lists) {
            this.lists = new ArrayList<>(lists);
            return this;
        }

        public Builder tags(List<String> tags) {
            this.tags = new ArrayList<>(tags);
            return this;
        }

        public Builder consentSource(String consentSource) {
            this.consentSource = consentSource;
            return this;
        }

        public Builder removeLists(List<String> removeLists) {
            this.removeLists = new ArrayList<>(removeLists);
            return this;
        }

        public Builder removeTags(List<String> removeTags) {
            this.removeTags = new ArrayList<>(removeTags);
            return this;
        }

        /** Unsubscribes the person from your newsletters. */
        public Builder unsubscribe() {
            this.status = "unsubscribed";
            return this;
        }

        public UpdateContactParams build() {
            return new UpdateContactParams(email, firstName, lastName, phone, locale,
                    fields == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(fields)),
                    lists == null ? null : List.copyOf(lists),
                    tags == null ? null : List.copyOf(tags),
                    consentSource,
                    removeLists == null ? null : List.copyOf(removeLists),
                    removeTags == null ? null : List.copyOf(removeTags),
                    status);
        }
    }
}
