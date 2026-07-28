package app.cairn.api.core.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables Spring's in-process {@code @Scheduled} support (D-028 MVP: background work runs in the single
 * api process, no separate worker container). Currently drives the notifications retention purge (F1).
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {}
