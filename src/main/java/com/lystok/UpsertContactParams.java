package com.lystok;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A person to add, or to update when the address is already there. Make one with
 * {@link #builder(String)}.
 *
 * @param fields Your own fields by key: strings, numbers or booleans; null removes a value
 * @param lists Lists to add the person to, by name or id
 * @param consentSource How the person agreed to get your emails; required for a new contact
 */
public record UpsertContactParams(
        String email,
        String firstName,
        String lastName,
        String phone,
        String locale,
        Map<String, Object> fields,
        List<String> lists,
        List<String> tags,
        String consentSource) {

    public static Builder builder(String email) {
        return new Builder(email);
    }

    public static final class Builder {
        private final String email;
        private String firstName;
        private String lastName;
        private String phone;
        private String locale;
        private Map<String, Object> fields;
        private List<String> lists;
        private List<String> tags;
        private String consentSource;

        private Builder(String email) {
            this.email = Objects.requireNonNull(email, "email is required");
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

        public UpsertContactParams build() {
            return new UpsertContactParams(email, firstName, lastName, phone, locale,
                    fields == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(fields)),
                    lists == null ? null : List.copyOf(lists),
                    tags == null ? null : List.copyOf(tags),
                    consentSource);
        }
    }
}
