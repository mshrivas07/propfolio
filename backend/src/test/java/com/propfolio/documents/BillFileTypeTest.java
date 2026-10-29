package com.propfolio.documents;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class BillFileTypeTest {

    @Test
    void recognisesPdf() {
        assertThat(BillFileType.detect(TestFiles.pdf("x"))).contains(BillFileType.PDF);
    }

    @Test
    void recognisesPdfWithLeadingBytes() {
        byte[] content = ("\r\n\r\n" + "%PDF-1.7\n").getBytes(StandardCharsets.US_ASCII);
        assertThat(BillFileType.detect(content)).contains(BillFileType.PDF);
    }

    @Test
    void ignoresPdfMarkerFarIntoTheFile() {
        byte[] content = ("x".repeat(2000) + "%PDF-1.7").getBytes(StandardCharsets.US_ASCII);
        assertThat(BillFileType.detect(content)).isEmpty();
    }

    @Test
    void recognisesPng() {
        assertThat(BillFileType.detect(TestFiles.png())).contains(BillFileType.PNG);
    }

    @Test
    void recognisesJpeg() {
        assertThat(BillFileType.detect(TestFiles.jpeg())).contains(BillFileType.JPEG);
    }

    @Test
    void rejectsTextHtmlAndOtherFormats() {
        assertThat(BillFileType.detect("hello, this is a bill".getBytes(StandardCharsets.UTF_8))).isEmpty();
        assertThat(BillFileType.detect("<html><body>%PDF</body></html>".getBytes(StandardCharsets.UTF_8))).isEmpty();
        assertThat(BillFileType.detect(new byte[] {'G', 'I', 'F', '8', '9', 'a'})).isEmpty();
        assertThat(BillFileType.detect(new byte[] {'P', 'K', 3, 4})).isEmpty(); // zip / docx
    }

    @Test
    void rejectsEmptyAndTruncatedContent() {
        assertThat(BillFileType.detect(new byte[0])).isEmpty();
        assertThat(BillFileType.detect(null)).isEmpty();
        assertThat(BillFileType.detect(new byte[] {(byte) 0x89, 'P', 'N'})).isEmpty();
    }

    @Test
    void cleanFilenameKeepsOnlyTheLastPathPart() {
        assertThat(BillService.cleanFilename("C:\\Users\\me\\hydro.pdf", BillFileType.PDF)).isEqualTo("hydro.pdf");
        assertThat(BillService.cleanFilename("../../etc/passwd", BillFileType.PDF)).isEqualTo("passwd");
        assertThat(BillService.cleanFilename("  ", BillFileType.PNG)).isEqualTo("bill.png");
        assertThat(BillService.cleanFilename(null, BillFileType.JPEG)).isEqualTo("bill.jpg");
        assertThat(BillService.cleanFilename("a\u0000b.pdf", BillFileType.PDF)).isEqualTo("ab.pdf");
    }
}
