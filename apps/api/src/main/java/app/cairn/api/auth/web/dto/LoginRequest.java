package app.cairn.api.auth.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Password-login payload. */
public record LoginRequest(
        @NotBlank @Email @Size(max = 320) String email, @NotBlank @Size(max = 200) String password) {}
