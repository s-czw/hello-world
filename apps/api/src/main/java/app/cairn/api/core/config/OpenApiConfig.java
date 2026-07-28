package app.cairn.api.core.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Fixed OpenAPI document metadata so the emitted {@code openapi.json} is stable across builds (the CI
 * drift check regenerates it and diffs). Version is pinned deliberately — it is not the app version.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI cairnOpenApi() {
        return new OpenAPI()
                // Pin a relative server so the emitted spec is byte-stable (springdoc otherwise injects
                // the runtime host:port, which varies and would defeat the CI client-drift check).
                .servers(List.of(new Server().url("/")))
                .info(new Info()
                        .title("Cairn API")
                        .version("v1")
                        .description("Cairn — self-hosted program & project management (M1)."));
    }
}
