package app.cairn.api.core.config;

import app.cairn.api.core.org.OrgContextInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Registers the org-context interceptor for the versioned API surface. */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final OrgContextInterceptor orgContextInterceptor;

    public WebMvcConfig(OrgContextInterceptor orgContextInterceptor) {
        this.orgContextInterceptor = orgContextInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(orgContextInterceptor).addPathPatterns("/api/v1/**");
    }
}
