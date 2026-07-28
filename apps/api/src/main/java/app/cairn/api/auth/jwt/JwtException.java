package app.cairn.api.auth.jwt;

/** Thrown when an access token is missing, malformed, tampered with, or expired. */
public class JwtException extends RuntimeException {
    public JwtException(String message) {
        super(message);
    }
}
