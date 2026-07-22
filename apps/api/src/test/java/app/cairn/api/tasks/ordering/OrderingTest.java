package app.cairn.api.tasks.ordering;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Exhaustive checks on the fractional ordering util: append, prepend, between, repeated between until
 * keys grow long enough to trigger a rebalance, equal-neighbour tie handling, and a randomized
 * fuzz that keeps a sorted list consistent across thousands of insertions.
 */
class OrderingTest {

    // --- basic shape ---------------------------------------------------------

    @Test
    void firstKeyIsNonEmptyAndCanonical() {
        String k = Ordering.between(null, null);
        assertThat(k).isNotEmpty();
        assertThat(endsInZero(k)).isFalse();
        assertThat(isBase62(k)).isTrue();
    }

    @Test
    void appendProducesStrictlyIncreasingKeys() {
        String prev = Ordering.between(null, null);
        for (int i = 0; i < 500; i++) {
            String next = Ordering.after(prev);
            assertThat(next.compareTo(prev)).isPositive();
            assertThat(endsInZero(next)).isFalse();
            assertThat(isBase62(next)).isTrue();
            prev = next;
        }
    }

    @Test
    void prependProducesStrictlyDecreasingKeys() {
        String prev = Ordering.between(null, null);
        for (int i = 0; i < 500; i++) {
            String next = Ordering.before(prev);
            assertThat(next.compareTo(prev)).isNegative();
            assertThat(next).isNotEmpty();
            assertThat(endsInZero(next)).isFalse();
            assertThat(isBase62(next)).isTrue();
            prev = next;
        }
    }

    @Test
    void betweenTwoKeysLandsStrictlyBetween() {
        String lo = Ordering.between(null, null);
        String hi = Ordering.after(lo);
        String mid = Ordering.between(lo, hi);
        assertThat(mid.compareTo(lo)).isPositive();
        assertThat(mid.compareTo(hi)).isNegative();
    }

    // --- repeated between until rebalance is warranted -----------------------

    @Test
    void repeatedBetweenGrowsKeysUntilRebalanceThenResetsShort() {
        // Insert repeatedly into the same shrinking gap; keys must lengthen until needsRebalance fires.
        String lo = Ordering.between(null, null);
        String hi = Ordering.after(lo);
        String cur = hi;
        int iterations = 0;
        while (!Ordering.needsRebalance(cur) && iterations < 5_000) {
            cur = Ordering.between(lo, cur); // keep bisecting toward lo
            assertThat(cur.compareTo(lo)).isPositive();
            iterations++;
        }
        assertThat(Ordering.needsRebalance(cur))
                .as("repeated between eventually produces a rebalance-worthy long key")
                .isTrue();

        // Rebalance yields short, strictly ascending, canonical keys.
        List<String> fresh = Ordering.rebalance(50);
        assertThat(fresh).hasSize(50);
        for (int i = 0; i < fresh.size(); i++) {
            assertThat(Ordering.needsRebalance(fresh.get(i))).isFalse();
            assertThat(endsInZero(fresh.get(i))).isFalse();
            assertThat(isBase62(fresh.get(i))).isTrue();
            if (i > 0) {
                assertThat(fresh.get(i).compareTo(fresh.get(i - 1))).isPositive();
            }
        }
    }

    // --- equal / inverted neighbour tie handling -----------------------------

    @Test
    void equalNeighboursDoNotThrowAndYieldKeyAfterThem() {
        String k = Ordering.between(null, null);
        String tie = Ordering.between(k, k); // equal neighbours (a tie)
        assertThat(tie).isNotEmpty();
        assertThat(tie.compareTo(k)).isPositive();
        assertThat(endsInZero(tie)).isFalse();
    }

    @Test
    void invertedNeighboursFallBackToAfterLower() {
        String lo = Ordering.between(null, null);
        String hi = Ordering.after(lo);
        // Deliberately inverted (hi passed as lower, lo as upper).
        String k = Ordering.between(hi, lo);
        assertThat(k).isNotEmpty();
        assertThat(k.compareTo(hi)).isPositive();
    }

    // --- generateN ------------------------------------------------------------

    @Test
    void generateNBetweenBoundsIsAscendingAndInRange() {
        String lo = "V";
        String hi = "k";
        List<String> keys = Ordering.generateN(lo, hi, 32);
        assertThat(keys).hasSize(32);
        String prev = lo;
        for (String k : keys) {
            assertThat(k.compareTo(prev)).isPositive();
            assertThat(k.compareTo(hi)).isNegative();
            prev = k;
        }
    }

    @Test
    void rebalanceZeroAndOne() {
        assertThat(Ordering.rebalance(0)).isEmpty();
        assertThat(Ordering.rebalance(1)).hasSize(1);
    }

    // --- randomized fuzz: a sorted list stays totally ordered ----------------

    @Test
    void randomizedInsertionsKeepListTotallyOrdered() {
        Random rnd = new Random(42);
        List<String> keys = new ArrayList<>();
        keys.add(Ordering.between(null, null));

        for (int i = 0; i < 4_000; i++) {
            int pos = rnd.nextInt(keys.size() + 1); // insertion slot [0, size]
            String lo = pos == 0 ? null : keys.get(pos - 1);
            String hi = pos == keys.size() ? null : keys.get(pos);
            String k = Ordering.between(lo, hi);
            // strictly between its neighbours
            if (lo != null) {
                assertThat(k.compareTo(lo)).isPositive();
            }
            if (hi != null) {
                assertThat(k.compareTo(hi)).isNegative();
            }
            keys.add(pos, k);
        }

        // whole list is strictly ascending and every key is canonical base-62
        for (int i = 1; i < keys.size(); i++) {
            assertThat(keys.get(i).compareTo(keys.get(i - 1)))
                    .as("keys[%d] > keys[%d]", i, i - 1)
                    .isPositive();
        }
        for (String k : keys) {
            assertThat(k).isNotEmpty();
            assertThat(isBase62(k)).isTrue();
            assertThat(endsInZero(k)).isFalse();
        }
    }

    // --- helpers -------------------------------------------------------------

    private static boolean endsInZero(String k) {
        return !k.isEmpty() && k.charAt(k.length() - 1) == Ordering.ZERO;
    }

    private static boolean isBase62(String k) {
        for (int i = 0; i < k.length(); i++) {
            if (Ordering.DIGITS.indexOf(k.charAt(i)) < 0) {
                return false;
            }
        }
        return true;
    }
}
