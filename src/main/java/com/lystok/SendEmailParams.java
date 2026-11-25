package com.lystok;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One email to send. Give {@code subject} with {@code html} and/or {@code text}, or a
 * {@code templateId} with its {@code data}. Make one with {@link #builder()}.
 *
 * @param from Such as {@code Shop <orders@shop.example>}, on a verified domain
 * @param templateId A template's id: Templates → ⋯ → Copy ID
 * @param data Values for the template's variables, such as {@code first_name}
 * @param locale The template's language: {@code uk}, {@code en}, {@code pl}, {@code de},
 *     {@code es}, {@code fr} or {@code ru}
 * @param tags Your labels, sent back in webhooks
 */
public record SendEmailParams(
        String from,
        String to,
        String subject,
        String html,
        String text,
        String templateId,
        Map<String, Object> data,
        String locale,
        String replyTo,
        Map<String, String> headers,
        Map<String, String> tags,
        Tracking tracking,
        List<Attachment> attachments) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String from;
        private String to;
        private String subject;
        private String html;
        private String text;
        private String templateId;
        private Map<String, Object> data;
        private String locale;
        private String replyTo;
        private Map<String, String> headers;
        private Map<String, String> tags;
        private Tracking tracking;
        private List<Attachment> attachments;

        private Builder() {
        }

        public Builder from(String from) {
            this.from = from;
            return this;
        }

        public Builder to(String to) {
            this.to = to;
            return this;
        }

        public Builder subject(String subject) {
            this.subject = subject;
            return this;
        }

        public Builder html(String html) {
            this.html = html;
            return this;
        }

        public Builder text(String text) {
            this.text = text;
            return this;
        }

        public Builder templateId(String templateId) {
            this.templateId = templateId;
            return this;
        }

        /** All values for the template's variables; nested maps and lists are fine. */
        public Builder data(Map<String, ?> data) {
            this.data = data == null ? null : new LinkedHashMap<>(data);
            return this;
        }

        /** One value for the template's variables. */
        public Builder data(String key, Object value) {
            if (this.data == null) {
                this.data = new LinkedHashMap<>();
            }
            this.data.put(key, value);
            return this;
        }

        public Builder locale(String locale) {
            this.locale = locale;
            return this;
        }

        public Builder replyTo(String replyTo) {
            this.replyTo = replyTo;
            return this;
        }

        public Builder headers(Map<String, String> headers) {
            this.headers = headers == null ? null : new LinkedHashMap<>(headers);
            return this;
        }

        public Builder header(String name, String value) {
            if (this.headers == null) {
                this.headers = new LinkedHashMap<>();
            }
            this.headers.put(name, value);
            return this;
        }

        public Builder tags(Map<String, String> tags) {
            this.tags = tags == null ? null : new LinkedHashMap<>(tags);
            return this;
        }

        public Builder tag(String key, String value) {
            if (this.tags == null) {
                this.tags = new LinkedHashMap<>();
            }
            this.tags.put(key, value);
            return this;
        }

        public Builder tracking(boolean opens, boolean clicks) {
            this.tracking = new Tracking(opens, clicks);
            return this;
        }

        public Builder attachments(List<Attachment> attachments) {
            this.attachments = attachments == null ? null : new ArrayList<>(attachments);
            return this;
        }

        public Builder attachment(Attachment attachment) {
            if (this.attachments == null) {
                this.attachments = new ArrayList<>();
            }
            this.attachments.add(attachment);
            return this;
        }

        public SendEmailParams build() {
            Objects.requireNonNull(from, "from is required");
            Objects.requireNonNull(to, "to is required");
            return new SendEmailParams(from, to, subject, html, text, templateId,
                    data == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(data)),
                    locale, replyTo,
                    headers == null ? null : Map.copyOf(headers),
                    tags == null ? null : Map.copyOf(tags),
                    tracking,
                    attachments == null ? null : List.copyOf(attachments));
        }
    }
}
