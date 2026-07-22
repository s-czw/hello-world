package app.cairn.api.core.error;

import org.springframework.http.HttpStatus;

/** Base for domain errors that map to an RFC 9457 ProblemDetail. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
