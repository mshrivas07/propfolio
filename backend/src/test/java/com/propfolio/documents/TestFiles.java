package com.propfolio.documents;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Tiny made-up files with real signatures. Never use real bills in tests (the repo is public).
 */
public final class TestFiles {

    private TestFiles() {
    }

    /** A minimal PDF whose content differs by {@code marker}, so each call can give a new hash. */
    public static byte[] pdf(String marker) {
        return ("%PDF-1.4\n% sample bill " + marker + "\n%%EOF\n").getBytes(StandardCharsets.US_ASCII);
    }

    public static byte[] png() {
        return new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 'I', 'H', 'D', 'R'};
    }

    public static byte[] jpeg() {
        return new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F', 0};
    }

    /** A PDF one byte over the 10 MB limit. */
    public static byte[] oversizedPdf() {
        byte[] content = new byte[(int) BillService.MAX_FILE_BYTES + 1];
        Arrays.fill(content, (byte) ' ');
        byte[] header = "%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(header, 0, content, 0, header.length);
        return content;
    }
}
