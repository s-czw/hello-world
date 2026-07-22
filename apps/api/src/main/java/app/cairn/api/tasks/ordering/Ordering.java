package app.cairn.api.tasks.ordering;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Fractional index ordering over base-62 string keys (the "sort_key" of sections and tasks).
 *
 * <p>A key is a string of base-62 digits interpreted as the fractional part of a number in
 * {@code (0, 1)} (i.e. {@code "V"} means {@code 0.V} in base 62). The alphabet is ordered by ASCII
 * ({@code 0-9A-Za-z}) so a plain lexicographic {@link String#compareTo} on the keys matches numeric
 * order — which is exactly what a SQL {@code ORDER BY sort_key} does. Between any two keys there is
 * always room for another, so an insert/move never has to renumber its neighbours; keys only grow in
 * length, and the {@linkplain #needsRebalance(String) rebalance} path resets a run of long keys back
 * to short ones.
 *
 * <p>The core is {@link #between(String, String)} (a.k.a. {@code generateKeyBetween}); {@link #before}
 * and {@link #after} are the open-ended variants. All generated keys are canonical: they never end in
 * the lowest digit ({@code '0'}), so distinct values never share a lexicographic representation.
 *
 * <p>This class is pure and has no dependency on the database or the org seam; the {@code tasks}
 * service computes keys with it and persists them through {@code OrgScopedDsl}.
 */
public final class Ordering {

    /** Base-62 digits in ascending ASCII order, so lexicographic string order == numeric order. */
    static final String DIGITS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    static final char ZERO = DIGITS.charAt(0);

    /** Keys longer than this signal that their neighbourhood should be rebalanced. */
    public static final int MAX_KEY_LENGTH = 40;

    private Ordering() {}

    /**
     * A key strictly ordered between {@code lo} and {@code hi} (either may be {@code null}/blank to mean
     * "open on that side"). Tolerant of a degenerate range: if {@code lo >= hi} (equal neighbours from a
     * concurrent move, or an inverted anchor pair) it falls back to a key just after {@code lo}, so the
     * result is always a valid, non-empty key rather than an exception.
     */
    public static String between(String lo, String hi) {
        String a = (lo == null || lo.isEmpty()) ? null : lo;
        String b = (hi == null || hi.isEmpty()) ? null : hi;
        if (a == null && b == null) {
            return midpoint("", null);
        }
        if (a == null) {
            return midpoint("", b);
        }
        if (b == null) {
            return midpoint(a, null);
        }
        if (a.compareTo(b) < 0) {
            return midpoint(a, b);
        }
        // Equal or inverted neighbours (tie / race): place just after the lower bound.
        return midpoint(a, null);
    }

    /** A key ordered strictly before {@code hi} (prepend). */
    public static String before(String hi) {
        return between(null, hi);
    }

    /** A key ordered strictly after {@code lo} (append). */
    public static String after(String lo) {
        return between(lo, null);
    }

    /** True when a key has grown long enough that its section should be rebalanced. */
    public static boolean needsRebalance(String key) {
        return key != null && key.length() > MAX_KEY_LENGTH;
    }

    /** {@code n} evenly distributed, short, strictly-ascending keys — used to reset a section. */
    public static List<String> rebalance(int n) {
        return generateN(null, null, n);
    }

    /**
     * {@code n} keys strictly ordered between {@code lo} and {@code hi}, balanced so they stay short
     * (bisection recursion). Returns them in ascending order.
     */
    public static List<String> generateN(String lo, String hi, int n) {
        if (n <= 0) {
            return List.of();
        }
        if (n == 1) {
            return List.of(between(lo, hi));
        }
        String a = (lo == null || lo.isEmpty()) ? null : lo;
        String b = (hi == null || hi.isEmpty()) ? null : hi;
        ArrayList<String> out = new ArrayList<>(n);
        if (b == null) {
            String c = between(a, null);
            out.add(c);
            for (int i = 1; i < n; i++) {
                c = between(c, null);
                out.add(c);
            }
            return out;
        }
        if (a == null) {
            String c = between(null, b);
            out.add(c);
            for (int i = 1; i < n; i++) {
                c = between(null, c);
                out.add(c);
            }
            Collections.reverse(out);
            return out;
        }
        int mid = n / 2;
        String c = between(a, b);
        out.addAll(generateN(a, c, mid));
        out.add(c);
        out.addAll(generateN(c, b, n - mid - 1));
        return out;
    }

    // ---------------------------------------------------------------------
    // Core: a fractional string strictly between a and b, never ending in '0'.
    // a is a (possibly empty) digit string; b is a digit string or null (== +infinity).
    // Adapted from the well-known fractional-indexing midpoint algorithm.
    // ---------------------------------------------------------------------
    private static String midpoint(String a, String b) {
        if (b != null) {
            // Descend past the longest common prefix, then split the first differing position.
            int n = 0;
            while (n < b.length() && charAt(a, n) == b.charAt(n)) {
                n++;
            }
            if (n > 0) {
                String aTail = a.length() > n ? a.substring(n) : "";
                return b.substring(0, n) + midpoint(aTail, b.substring(n));
            }
        }
        int digitA = a.isEmpty() ? 0 : DIGITS.indexOf(a.charAt(0));
        int digitB = (b != null && !b.isEmpty()) ? DIGITS.indexOf(b.charAt(0)) : DIGITS.length();
        if (digitB - digitA > 1) {
            int midDigit = (int) Math.round(0.5 * (digitA + digitB));
            return String.valueOf(DIGITS.charAt(midDigit));
        }
        // First digits are consecutive: keep b's leading digit if it has depth to spare,
        // otherwise recurse into a's fractional tail one place deeper.
        if (b != null && b.length() > 1) {
            return b.substring(0, 1);
        }
        String aTail = a.isEmpty() ? "" : a.substring(1);
        return DIGITS.charAt(digitA) + midpoint(aTail, null);
    }

    private static char charAt(String s, int i) {
        return i < s.length() ? s.charAt(i) : ZERO;
    }
}
