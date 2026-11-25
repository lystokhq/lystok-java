package com.lystok;

import java.util.Base64;

/**
 * A file attached to an email.
 *
 * @param content The file, base64-encoded; {@link #of(String, byte[])} encodes it for you
 * @param contentType Such as {@code application/pdf}; guessed from the name when null
 */
public record Attachment(String filename, String content, String contentType) {
    /** An attachment from the file's bytes. */
    public static Attachment of(String filename, byte[] bytes) {
        return new Attachment(filename, Base64.getEncoder().encodeToString(bytes), null);
    }

    /** An attachment from the file's bytes, with its type. */
    public static Attachment of(String filename, byte[] bytes, String contentType) {
        return new Attachment(filename, Base64.getEncoder().encodeToString(bytes), contentType);
    }
}
