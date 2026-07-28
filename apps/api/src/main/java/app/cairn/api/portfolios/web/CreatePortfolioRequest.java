package app.cairn.api.portfolios.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Body for {@code POST /portfolios}. Owner defaults to the caller when omitted. */
public record CreatePortfolioRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 4000) String description,
        @Size(max = 32) String color,
        UUID ownerId) {}
