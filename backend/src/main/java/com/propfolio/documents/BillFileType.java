package com.propfolio.documents;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

/**
 * File types accepted for bills, recognised by their first bytes (file signature),
 * never by the name or the Content-Type the browser sends.
 */
public enum BillFileType {

    PDF("application/pdf", "pdf"),
    PNG("image/png", "png"),
    JPEG("image/jpeg", "jpg");

    private static final byte[] PDF_SIGNATURE = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] JPEG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    /** Some PDF writers put a few bytes before "%PDF-"; readers accept it within the first 1024. */
    private static final int PDF_SEARCH_LIMIT = 1024;

    private final String contentType;
    private final String extension;

    BillFileType(String contentType, String extension) {
        this.contentType = contentType;
        this.extension = extension;
    }

    public String contentType() {
        return contentType;
    }

    public String extension() {
        return extension;
    }

    public static Optional<BillFileType> detect(byte[] content) {
        if (content == null) {
            return Optional.empty();
        }
        if (startsWith(content, PNG_SIGNATURE)) {
            return Optional.of(PNG);
        }
        if (startsWith(content, JPEG_SIGNATURE)) {
            return Optional.of(JPEG);
        }
        if (indexOf(content, PDF_SIGNATURE, PDF_SEARCH_LIMIT) >= 0) {
            return Optional.of(PDF);
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] content, byte[] prefix) {
        return content.length >= prefix.length
                && Arrays.equals(content, 0, prefix.length, prefix, 0, prefix.length);
    }

    private static int indexOf(byte[] content, byte[] needle, int limit) {
        int last = Math.min(content.length, limit) - needle.length;
        for (int i = 0; i <= last; i++) {
            if (Arrays.equals(content, i, i + needle.length, needle, 0, needle.length)) {
                return i;
            }
        }
        return -1;
    }
}
