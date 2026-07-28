package app.cairn.api.core.web;

import java.util.Map;

/**
 * Enveloped API response: {@code { "data": ..., "meta": {...} }} (arch §4).
 * Timestamps are ISO-8601 UTC; list responses put {@code nextCursor} in meta.
 */
public record ApiResponse<T>(T data, Map<String, Object> meta) {

    public static <T> ApiResponse<T> of(T data) {
        return new ApiResponse<>(data, Map.of());
    }

    public static <T> ApiResponse<T> of(T data, Map<String, Object> meta) {
        return new ApiResponse<>(data, meta == null ? Map.of() : meta);
    }

    /** List envelope with cursor pagination metadata ({@code nextCursor} may be null). */
    public static <T> ApiResponse<T> page(T data, String nextCursor) {
        java.util.HashMap<String, Object> meta = new java.util.HashMap<>();
        meta.put("nextCursor", nextCursor);
        return new ApiResponse<>(data, meta);
    }
}
