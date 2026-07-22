package app.cairn.api.core.id;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * Minimal UUIDv7 generator (RFC 9562): 48-bit big-endian Unix millisecond timestamp,
 * 4-bit version (0b0111), 2-bit variant (0b10), and 74 bits of randomness.
 * Time-sortable and index-friendly, which is why ids are UUIDv7 across Cairn (D-008).
 */
public final class Uuid7 {

    private static final SecureRandom RANDOM = new SecureRandom();

    private Uuid7() {
    }

    public static UUID generate() {
        return generate(System.currentTimeMillis());
    }

    static UUID generate(long unixMillis) {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);

        // 48-bit timestamp in the first 6 bytes.
        bytes[0] = (byte) ((unixMillis >>> 40) & 0xFF);
        bytes[1] = (byte) ((unixMillis >>> 32) & 0xFF);
        bytes[2] = (byte) ((unixMillis >>> 24) & 0xFF);
        bytes[3] = (byte) ((unixMillis >>> 16) & 0xFF);
        bytes[4] = (byte) ((unixMillis >>> 8) & 0xFF);
        bytes[5] = (byte) (unixMillis & 0xFF);

        // Version 7 in the high nibble of byte 6.
        bytes[6] = (byte) ((bytes[6] & 0x0F) | 0x70);
        // Variant 10xx in the high bits of byte 8.
        bytes[8] = (byte) ((bytes[8] & 0x3F) | 0x80);

        long msb = 0;
        long lsb = 0;
        for (int i = 0; i < 8; i++) {
            msb = (msb << 8) | (bytes[i] & 0xFF);
        }
        for (int i = 8; i < 16; i++) {
            lsb = (lsb << 8) | (bytes[i] & 0xFF);
        }
        return new UUID(msb, lsb);
    }
}
