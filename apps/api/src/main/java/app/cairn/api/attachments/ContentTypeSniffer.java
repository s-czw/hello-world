package app.cairn.api.attachments;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

/**
 * Validates an upload by extension allowlist <em>and</em> a magic-byte content sniff, and derives a safe
 * stored content type. Rejects anything that looks like HTML/SVG/XML (script-injection surface) or an
 * executable/script (ELF, PE/DOS "MZ", Mach-O/Java {@code CAFEBABE}, {@code #!} shebang), regardless of
 * the claimed extension. Pure and dependency-free (no Tika/GPL concerns; {@code Files.probeContentType}
 * is unreliable in containers) so the rules are unit-testable and identical everywhere.
 */
public final class ContentTypeSniffer {

    private ContentTypeSniffer() {}

    /** Outcome of a check: allowed (with a safe content type) or rejected (with a reason). */
    public record Result(boolean allowed, String reason, String contentType) {}

    private static final Map<String, String> EXT_CONTENT_TYPE = Map.ofEntries(
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("gif", "image/gif"),
            Map.entry("webp", "image/webp"),
            Map.entry("bmp", "image/bmp"),
            Map.entry("pdf", "application/pdf"),
            Map.entry("txt", "text/plain"),
            Map.entry("md", "text/plain"),
            Map.entry("log", "text/plain"),
            Map.entry("csv", "text/csv"),
            Map.entry("json", "application/json"),
            Map.entry("zip", "application/zip"),
            Map.entry("doc", "application/msword"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("xls", "application/vnd.ms-excel"),
            Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry("ppt", "application/vnd.ms-powerpoint"),
            Map.entry("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"));

    private static final Set<String> ALLOWED_EXT = EXT_CONTENT_TYPE.keySet();

    public static Result check(String filename, byte[] head) {
        String ext = extensionOf(filename);
        if (ext == null || !ALLOWED_EXT.contains(ext)) {
            return new Result(false, "File type" + (ext == null ? "" : " ." + ext) + " is not allowed", null);
        }
        String danger = dangerReason(head);
        if (danger != null) {
            return new Result(false, "File content rejected: " + danger, null);
        }
        return new Result(true, null, EXT_CONTENT_TYPE.get(ext));
    }

    /** Lower-cased extension without the dot, or null when there is none. */
    public static String extensionOf(String filename) {
        if (filename == null) {
            return null;
        }
        String name = filename.trim();
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return null;
        }
        return name.substring(dot + 1).toLowerCase();
    }

    /** A short reason string if the bytes look dangerous, else null. */
    public static String dangerReason(byte[] head) {
        if (head == null || head.length == 0) {
            return null;
        }
        if (startsWith(head, new int[] {0x7F, 'E', 'L', 'F'})) {
            return "executable (ELF)";
        }
        if (startsWith(head, new int[] {'M', 'Z'})) {
            return "executable (PE/DOS)";
        }
        if (startsWith(head, new int[] {0xCA, 0xFE, 0xBA, 0xBE})
                || startsWith(head, new int[] {0xFE, 0xED, 0xFA, 0xCE})
                || startsWith(head, new int[] {0xCF, 0xFA, 0xED, 0xFE})) {
            return "executable (Mach-O/class)";
        }
        if (startsWith(head, new int[] {'#', '!'})) {
            return "script (shebang)";
        }
        String text = textPrefix(head);
        if (text.startsWith("<!doctype html")
                || text.startsWith("<html")
                || text.startsWith("<svg")
                || text.startsWith("<?xml")
                || text.contains("<script")) {
            return "markup/script (HTML/SVG/XML)";
        }
        return null;
    }

    private static boolean startsWith(byte[] head, int[] magic) {
        if (head.length < magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if ((head[i] & 0xFF) != (magic[i] & 0xFF)) {
                return false;
            }
        }
        return true;
    }

    /** Up to the first 256 bytes decoded as UTF-8, trimmed of leading BOM/whitespace and lower-cased. */
    private static String textPrefix(byte[] head) {
        int len = Math.min(head.length, 256);
        int start = 0;
        // skip a UTF-8 BOM
        if (len >= 3 && (head[0] & 0xFF) == 0xEF && (head[1] & 0xFF) == 0xBB && (head[2] & 0xFF) == 0xBF) {
            start = 3;
        }
        String s = new String(head, start, len - start, StandardCharsets.UTF_8);
        return s.replaceFirst("^\\s+", "").toLowerCase();
    }
}
