package app.cairn.api.core.id;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class Uuid7Test {

    @Test
    void hasVersion7AndRfc4122Variant() {
        UUID id = Uuid7.generate();
        assertThat(id.version()).isEqualTo(7);
        assertThat(id.variant()).isEqualTo(2); // 10xx = RFC 4122
    }

    @Test
    void encodesTheProvidedTimestampInTheHigh48Bits() {
        long millis = 0x0123456789ABL;
        UUID id = Uuid7.generate(millis);
        long extracted = id.getMostSignificantBits() >>> 16;
        assertThat(extracted).isEqualTo(millis);
    }

    @Test
    void idsAreTimeOrderedAcrossMonotonicTimestamps() {
        UUID earlier = Uuid7.generate(1_000L);
        UUID later = Uuid7.generate(2_000L);
        // Compare as unsigned by comparing the 48-bit timestamp prefix.
        long e = earlier.getMostSignificantBits() >>> 16;
        long l = later.getMostSignificantBits() >>> 16;
        assertThat(l).isGreaterThan(e);
    }

    @Test
    void generatesDistinctValues() {
        Set<UUID> seen = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            seen.add(Uuid7.generate());
        }
        assertThat(seen).hasSize(10_000);
    }
}
