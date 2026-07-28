package app.cairn.api.auth.web.dto;

/** Whether the instance still needs first-run setup (drives the /setup redirect on the web). */
public record BootstrapStatusResponse(boolean needsBootstrap) {}
