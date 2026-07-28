package app.cairn.api.core.error;

import org.springframework.http.HttpStatus;

/** 409 for state conflicts (e.g. org already bootstrapped, duplicate membership). */
public class ConflictException extends ApiException {

    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
