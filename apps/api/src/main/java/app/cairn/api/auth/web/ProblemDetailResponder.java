package app.cairn.api.auth.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

/**
 * Writes RFC 9457 problem+json for security-filter failures (401/403) that occur before the
 * DispatcherServlet, where {@code @RestControllerAdvice} cannot reach. Shape matches the advice output.
 */
final class ProblemDetailResponder {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ProblemDetailResponder() {}

    static void write(HttpServletResponse response, HttpStatus status, String detail) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "about:blank");
        body.put("title", status.getReasonPhrase());
        body.put("status", status.value());
        body.put("detail", detail);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        MAPPER.writeValue(response.getWriter(), body);
    }
}
