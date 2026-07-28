package app.cairn.api.attachments;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** Unit tests for the upload validator (C4): extension allowlist + magic-byte sniff. */
class ContentTypeSnifferTest {

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void plainTextIsAllowed() {
        ContentTypeSniffer.Result r = ContentTypeSniffer.check("notes.txt", bytes("hello world"));
        assertThat(r.allowed()).isTrue();
        assertThat(r.contentType()).isEqualTo("text/plain");
    }

    @Test
    void pngMagicIsAllowed() {
        byte[] png = new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};
        ContentTypeSniffer.Result r = ContentTypeSniffer.check("logo.png", png);
        assertThat(r.allowed()).isTrue();
        assertThat(r.contentType()).isEqualTo("image/png");
    }

    @Test
    void disallowedExtensionRejected() {
        ContentTypeSniffer.Result r = ContentTypeSniffer.check("index.html", bytes("<h1>hi</h1>"));
        assertThat(r.allowed()).isFalse();
        assertThat(r.reason()).contains("not allowed");
    }

    @Test
    void svgExtensionRejected() {
        ContentTypeSniffer.Result r = ContentTypeSniffer.check("pic.svg", bytes("<svg></svg>"));
        assertThat(r.allowed()).isFalse();
    }

    @Test
    void htmlContentInAllowedExtensionRejectedBySniff() {
        // an allowed extension (.png) but the bytes are HTML → rejected by the content sniff
        ContentTypeSniffer.Result r =
                ContentTypeSniffer.check("evil.png", bytes("<!DOCTYPE html><html><script>alert(1)</script>"));
        assertThat(r.allowed()).isFalse();
        assertThat(r.reason()).contains("rejected");
    }

    @Test
    void scriptTagAnywhereInHeadRejected() {
        ContentTypeSniffer.Result r =
                ContentTypeSniffer.check("data.csv", bytes("col1,col2\n<script>evil()</script>"));
        assertThat(r.allowed()).isFalse();
    }

    @Test
    void elfExecutableRejected() {
        byte[] elf = new byte[] {0x7F, 'E', 'L', 'F', 1, 1, 1, 0};
        ContentTypeSniffer.Result r = ContentTypeSniffer.check("payload.pdf", elf);
        assertThat(r.allowed()).isFalse();
        assertThat(r.reason()).contains("ELF");
    }

    @Test
    void shebangScriptRejected() {
        ContentTypeSniffer.Result r = ContentTypeSniffer.check("run.txt", bytes("#!/bin/sh\nrm -rf /"));
        assertThat(r.allowed()).isFalse();
    }

    @Test
    void extensionExtraction() {
        assertThat(ContentTypeSniffer.extensionOf("a/b/c.PNG")).isEqualTo("png");
        assertThat(ContentTypeSniffer.extensionOf("noext")).isNull();
        assertThat(ContentTypeSniffer.extensionOf("trailing.")).isNull();
    }
}
