package app.cairn.api.core.error;

import org.springframework.http.HttpStatus;

/**
 * 404 for both missing and inaccessible resources — we never leak existence
 * (CLAUDE.md conventions).
 */
public class NotFoundException extends ApiException {

    public NotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }

    public static NotFoundException of(String resource) {
        return new NotFoundException(resource + " not found");
    }
}
