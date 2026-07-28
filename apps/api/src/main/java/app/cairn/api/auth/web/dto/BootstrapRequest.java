package app.cairn.api.auth.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** First-run payload: names the org and creates its first admin. */
public record BootstrapRequest(
        @NotBlank @Size(max = 200) String orgName,
        @NotBlank @Size(max = 200) String adminName,
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(min = 8, max = 200) String password) {}
