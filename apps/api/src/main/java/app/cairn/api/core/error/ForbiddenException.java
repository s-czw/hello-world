package app.cairn.api.core.error;

import org.springframework.http.HttpStatus;

/** 403 when an authenticated principal lacks the required role/permission. */
public class ForbiddenException extends ApiException {

    public ForbiddenException(String message) {
        super(HttpStatus.FORBIDDEN, message);
    }
}
